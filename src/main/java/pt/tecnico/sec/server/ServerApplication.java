package pt.tecnico.sec.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.ObjectMapperHandler;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.client.LocationReport;
import pt.tecnico.sec.client.ObtainLocationRequest;
import pt.tecnico.sec.client.SecureMessage;
import pt.tecnico.sec.client.WitnessProofsRequest;
import pt.tecnico.sec.healthauthority.ObtainUsersRequest;
import pt.tecnico.sec.server.exception.ReportNotAcceptableException;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static pt.tecnico.sec.Constants.*;

@SpringBootApplication
public class ServerApplication {

    private static KeyPair _keyPair;
    private static int _serverId;
    private static int _serverCount;
    private static int _userCount;

    private final int POW_N = 2;

    public final RestTemplate _restTemplate = new RestTemplate();

    private static final Map<Integer, SecretKey> _secretKeys = new HashMap<>();
    private static final Map<Integer, Integer> _secretKeysUsages = new HashMap<>();

    public static void main(String[] args) {
        try {
            // parse arguments
            _serverId = Integer.parseInt(args[0]);
            _serverCount = Integer.parseInt(args[1]);
            _userCount = Integer.parseInt(args[2]);

            if (_serverId >= _serverCount)
                throw new NumberFormatException("Server ID must be lower than the number of servers");

            // set database information
            int serverPort = SERVER_BASE_PORT + _serverId;
            Map<String, Object> defaults = new HashMap<>();
            defaults.put("server.port", serverPort);
            defaults.put("spring.jpa.hibernate.ddl-auto", "update");
            defaults.put("spring.datasource.url", "jdbc:mysql://${MYSQL_HOST:localhost}:3306/sec" + _serverId);
            defaults.put("spring.datasource.username", "user");
            defaults.put("spring.datasource.password", "pass");

            SpringApplication springApplication = new SpringApplication(ServerApplication.class);
            springApplication.setDefaultProperties(defaults);
            springApplication.run(args);
        } 
        catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(SERVER_USAGE);
        }
    }


    /* ========================================================== */
    /* ====[               Getters and Setters              ]==== */
    /* ========================================================== */

    public static int getId() {
        return _serverId;
    }

    public static int getUserCount() {
        return _userCount;
    }

    public static KeyPair getKeyPair() throws IOException, GeneralSecurityException {
        if (_keyPair == null) fetchRSAKeyPair();
        return _keyPair;
    }

    public static PrivateKey getPrivateKey() throws IOException, GeneralSecurityException {
        return getKeyPair().getPrivate();
    }

    public SecretKey getSecretKey(int id) {
        return _secretKeys.get(id);
    }

    public void saveSecretKey(int id, SecretKey secretKey) {
        _secretKeys.put(id, secretKey);
        if (id >= 1000) _secretKeysUsages.put(id, 0); // keep usage count for server keys
    }


    /* ========================================================== */
    /* ====[              Auxiliary functions               ]==== */
    /* ========================================================== */

    public static void fetchRSAKeyPair() throws IOException, GeneralSecurityException {
        // get server's keyPair
        String keysPath = KEYS_PATH + "s" + _serverId;
        _keyPair = RSAKeyGenerator.readKeyPair(keysPath + ".pub", keysPath + ".priv");
    }

    public String getServerURL(int serverId) {
        int serverPort = SERVER_BASE_PORT + serverId;
        return "http://localhost:" + serverPort;
    }

    public void serverSecretKeyUsed(int id) {
        assert id >= 1000;
        _secretKeysUsages.put(id, _secretKeysUsages.get(id)+1); // update secret key usages
    }

    /* ========================================================== */
    /* ====[                Atomic Registers                ]==== */
    /* ========================================================== */

    private SecureMessage postToServer(int serverId, byte[] messageBytes, String endpoint) throws Exception {
        updateServerSecretKey(serverId+1000);
        SecureMessage secureRequest = cipherAndSignMessage(serverId+1000, messageBytes);
        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + endpoint, request, SecureMessage.class);
        serverSecretKeyUsed(serverId+1000);
        return secureResponse;
    }

    public void broadcastWrite(DBLocationReport locationReport) throws Exception {
        // setup broadcast
        int acks = 0;
        int my_ts = locationReport.get_timestamp() + 1;
        locationReport.set_timestamp(my_ts);

        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(locationReport);

        System.out.println("Broadcasting write...");
        for (int serverId = 0; serverId < _serverCount; serverId++) {
            try {
                // send report
                SecureMessage secureResponse = postToServer(serverId, bytes, "/broadcast-write");
                byte[] responseBytes = decipherAndVerifyMessage(secureResponse);
                if (responseBytes != null && ObjectMapperHandler.getIntFromBytes(responseBytes) == my_ts) acks += 1;

            } catch(IllegalArgumentException e) {
                throw e;
            } catch(Exception e) {
                System.out.println(e.getMessage());
            } // we don't care if some servers fail, we only need (N+f)/2 to succeed
        }

        if (acks <= (_serverCount + FAULTS) / 2 )
            throw new Exception("Write operation broadcast was unsuccessful");
        // FIXME : Dar rebroadcast?
    }


    public DBLocationReport broadcastRead(ObtainLocationRequest request) throws Exception {
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(request);

        ArrayList<DBLocationReport> readList = new ArrayList<>();

        // get reports
        System.out.println("Broadcasting read...");
        for (int serverId = 0; serverId < _serverCount; serverId++) {
            try{
                // send request
                SecureMessage secureResponse = postToServer(serverId, bytes, "/broadcast-read");
                DBLocationReport locationReport = decipherAndVerifyServerWrite(secureResponse);
                checkObtainDBReportResponse(request, locationReport);
                readList.add(locationReport);

                if (readList.size() > (_serverCount+FAULTS)/2) break;

            } catch(IllegalArgumentException e) {
                throw e;
            } catch(Exception e) {
                System.out.println(e.getMessage());
            }
        }

        if (readList.size() <= (_serverCount + FAULTS) / 2)
            throw new Exception("Read operation broadcast was unsuccessful");
        // FIXME : Dar rebroadcast?

        // Choose the report with the largest timestamp
        DBLocationReport finalLocationReport = readList.get(0);
        for (DBLocationReport locationReport : readList) {
            if (locationReport == null) continue;
            if (finalLocationReport == null || locationReport.get_timestamp() > finalLocationReport.get_timestamp())
                finalLocationReport = locationReport;
        }

        // Atomic Register: Write-back phase after Read
        if (finalLocationReport != null)
            broadcastWrite(finalLocationReport);

        return finalLocationReport;

    }

    public void checkObtainDBReportResponse(ObtainLocationRequest request, DBLocationReport response) {
        if (response == null) return;
        // Check content
        if (response.get_userId() != request.get_userId() || response.get_epoch() != request.get_epoch())
            throw new IllegalArgumentException("Bad server response!");
    }


    /* ========================================================== */
    /* ====[               Handle Secret Keys               ]==== */
    /* ========================================================== */

    public boolean serverSecretKeyValid(int id) {
        assert id >= 1000;
        return _secretKeys.get(id) != null && _secretKeysUsages.get(id) <= SECRET_KEY_DURATION;
    }

    private byte[] sendSecretKey(int serverId, SecretKey keyToSend) throws Exception {
        PublicKey serverKey = RSAKeyGenerator.readServerPublicKey(serverId);
        System.out.println("oi key0A " + serverId);
        SecureMessage secureRequest = new SecureMessage(_serverId+1000, keyToSend, serverKey, _keyPair.getPrivate());

        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + "/secret-key", request, SecureMessage.class);

        System.out.println("oi key0B " + serverId);
        // Check response's signature and decipher TODO freshness
        assert secureResponse != null;
        return secureResponse.decipherAndVerify( keyToSend, serverKey);
    }

    public void updateServerSecretKey(int serverId) throws Exception {

        if (!serverSecretKeyValid(serverId)) {
            // Generate secret key
            SecretKey newSecretKey = AESKeyGenerator.makeAESKey();

            // Send key
            byte[] responseBytes = sendSecretKey(serverId-1000, newSecretKey);

            // Check response
            if (responseBytes == null || !ObjectMapperHandler.getStringFromBytes(responseBytes).equals("OK"))
                throw new IllegalArgumentException("Error exchanging new secret key");

            // Success! Update key
            saveSecretKey(serverId, newSecretKey);
        }
    }


    /* ========================================================== */
    /* ====[          Decipher and Verify Requests          ]==== */
    /* ========================================================== */

    public byte[] decipherAndVerifyMessage(SecureMessage secureMessage) throws Exception {
        int senderId = secureMessage.get_senderId();
        PublicKey verifyKey = getVerifyKey(senderId);
        return secureMessage.decipherAndVerify( _secretKeys.get(senderId), verifyKey );
    }

    public ObtainLocationRequest decipherAndVerifyReportRequest(SecureMessage secureMessage, boolean isClient) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);

        // Check Proof of Work
        if (isClient) messageBytes = checkProofOfWork(messageBytes);

        ObtainLocationRequest request = ObtainLocationRequest.getFromBytes(messageBytes);

        // check sender
        int sender_id = secureMessage.get_senderId();
        if ( !(fromHA(sender_id) || fromServer(sender_id) || fromSelf(sender_id, request.get_userId())) )
            throw new IllegalArgumentException("Cannot request reports from other users.");

        return request;
    }

    public DBLocationReport decipherAndVerifyReport(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);

        // Check Proof of Work
        messageBytes = checkProofOfWork(messageBytes);

        LocationReport locationReport = LocationReport.getFromBytes(messageBytes);

        // check sender
        if (!fromSelf(secureMessage.get_senderId(), locationReport.get_userId()))
            throw new ReportNotAcceptableException("Cannot submit reports from other users");

        // check proofs signatures
        checkReportSignatures(locationReport);

        return new DBLocationReport(locationReport, secureMessage.get_signature());
    }

    public WitnessProofsRequest decipherAndVerifyProofsRequest(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);

        // Check Proof of Work
        messageBytes = checkProofOfWork(messageBytes);

        WitnessProofsRequest request = WitnessProofsRequest.getFromBytes(messageBytes);

        // check sender
        if (!fromSelf(secureMessage.get_senderId(), request.get_userId()))
            throw new IllegalArgumentException("Cannot request proofs from other users");

        return request;
    }

    public ObtainUsersRequest decipherAndVerifyUsersRequest(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        ObtainUsersRequest request = ObtainUsersRequest.getFromBytes(messageBytes);

        // check sender
        if (!fromHA(secureMessage.get_senderId()))
            throw new IllegalArgumentException("Only the health authority can make 'users at location' requests");

        return request;
    }

    public DBLocationReport decipherAndVerifyServerWrite(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        DBLocationReport dbLocationReport = DBLocationReport.getFromBytes(messageBytes);
        if (dbLocationReport == null) return null;

        // check sender
        if (!fromServer(secureMessage.get_senderId()))
            throw new ReportNotAcceptableException("Can only accept register writes from servers.");

        // check corresponding report proofs signatures
        checkReportSignatures(new LocationReport(dbLocationReport));

        return dbLocationReport;
    }

    public SecretKey decipherAndVerifyKey(SecureMessage secureMessage) throws Exception {
        int senderId = secureMessage.get_senderId();
        PublicKey verifyKey = getVerifyKey(senderId);
        return secureMessage.decipherAndVerifyKey( getPrivateKey(), verifyKey );
    }

    public SecureMessage cipherAndSignMessage(int receiverId, byte[] messageBytes) throws Exception {
        return new SecureMessage(_serverId + 1000, messageBytes, _secretKeys.get(receiverId), getPrivateKey());
    }

    public byte[] checkProofOfWork(byte[] message) throws Exception {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(message);

        // Check if the hash has n leading 0s
        boolean correct = true;
        for (int k=0; k<POW_N; k++){
            if (hash[k] != 0){
                correct = false;
            }
        }

        if (correct){
            // Return message without the nonce
            System.out.println("Correct Proof of Work");
            return Arrays.copyOfRange(message, 0, message.length-POW_N+2);
        } else { throw new Exception("Incorrect Proof of Work"); }

    }

    /* ===========[   Auxiliary   ]=========== */

    public boolean fromServer(int senderId) {
        return senderId >= 1000;
    }

    public boolean fromHA(int senderId) {
        return senderId == -1;
    }

    public boolean fromSelf(int senderId, int userId) {
        return senderId == userId;
    }

    public PublicKey getVerifyKey(int senderId) throws GeneralSecurityException, IOException {
        if (fromHA(senderId)) return RSAKeyGenerator.readHAKey();
        else if (fromServer(senderId)) {
            return RSAKeyGenerator.readServerPublicKey(senderId-1000);
        }
        else return RSAKeyGenerator.readClientPublicKey(senderId);
    }

    public void checkReportSignatures(LocationReport report) throws Exception {
        int validProofCount = report.verifyProofs();
        if (validProofCount <= BYZANTINE_USERS)
            throw new ReportNotAcceptableException("Not enough proofs to constitute an acceptable Location Report");
    }

}
