package pt.tecnico.sec.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.client.LocationReport;
import pt.tecnico.sec.client.ObtainLocationRequest;
import pt.tecnico.sec.client.SecureMessage;
import pt.tecnico.sec.client.WitnessProofsRequest;
import pt.tecnico.sec.healthauthority.ObtainUsersRequest;
import pt.tecnico.sec.server.exception.ReportNotAcceptableException;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static pt.tecnico.sec.Constants.*;

@SpringBootApplication
public class ServerApplication {

    private static KeyPair _keyPair;
    private static int _serverId;
    private static int _serverCount;
    private static int _userCount;

    public RestTemplate _restTemplate = new RestTemplate();

    private static Map<Integer, SecretKey> _clientSecretKeys = new HashMap<>();
    private static Map<Integer, SecretKey> _serverSecretKeys = new HashMap<>();
    private static Map<Integer, Integer> _serverSecretKeysUsages = new HashMap<>();

    public static void main(String[] args) {
        try {
            // get serverId to determine its port
            _serverId = Integer.parseInt(args[0]);
            _serverCount = Integer.parseInt(args[1]);
            _userCount = Integer.parseInt(args[2]);

            if (_serverId >= _serverCount)
                throw new NumberFormatException("Server ID must be lower than the number of servers");

            int serverPort = SERVER_BASE_PORT + _serverId;

            Map<String, Object> defaults = new HashMap<>();
            defaults.put("server.port", serverPort);
            defaults.put("spring.jpa.hibernate.ddl-auto", "update");
            defaults.put("spring.datasource.url", "jdbc:mysql://${MYSQL_HOST:localhost}:3306/sec" + _serverId);
            defaults.put("spring.datasource.username", "user");
            defaults.put("spring.datasource.password", "pass");

            SpringApplication springApplication = new SpringApplication(ServerApplication.class);
            //springApplication.setDefaultProperties(Collections.singletonMap("server.port", String.valueOf(serverPort)));
            springApplication.setDefaultProperties(defaults);
            springApplication.run(args);
        } 
        catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(SERVER_USAGE);
        }
    }


    /* ========================================================== */
    /* ====[              Auxiliary functions               ]==== */
    /* ========================================================== */

    public static int getId() {
        return _serverId;
    }

    public static int getUserCount() {
        return _userCount;
    }

    public static void fetchRSAKeyPair() throws IOException, GeneralSecurityException {
        // get server's keyPair
        String keysPath = KEYS_PATH + "s" + _serverId;
        _keyPair = RSAKeyGenerator.readKeyPair(keysPath + ".pub", keysPath + ".priv");
    }

    public static PrivateKey getPrivateKey() throws IOException, GeneralSecurityException {
        return getKeyPair().getPrivate();
    }

    public static KeyPair getKeyPair() throws IOException, GeneralSecurityException {
        if (_keyPair == null) fetchRSAKeyPair();
        return _keyPair;
    }

    public static PublicKey getHAPublicKey() throws GeneralSecurityException, IOException {
        String keyPath = KEYS_PATH + "ha.pub";
        return RSAKeyGenerator.readPublicKey(keyPath);
    }

    public SecretKey getClientSecretKey(int id) {
        return _clientSecretKeys.get(id);
    }

    public void saveClientSecretKey(int id, SecretKey secretKey) {
        _clientSecretKeys.put(id, secretKey);
    }

    public void saveServerSecretKey(int id, SecretKey secretKey) {
        _serverSecretKeys.put(id, secretKey);
        _serverSecretKeysUsages.put(id, 0);
    }

    public String getServerURL(int serverId) {
        int serverPort = SERVER_BASE_PORT + serverId;
        return "http://localhost:" + serverPort;
    }

    public Integer getIntFromBytes(byte[] bytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, Integer.class);
    }

    public String getStringFromBytes(byte[] bytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, String.class);
    }

    public void serverSecretKeyUsed(int id) {
        _serverSecretKeysUsages.put(id, _serverSecretKeysUsages.get(id)+1); // update secret key usages
    }


    /* ========================================================== */
    /* ====[              Secure communication              ]==== */
    /* ========================================================== */

    public boolean secretKeyValid(int id) {
        return _serverSecretKeys.get(id) != null && _serverSecretKeysUsages.get(id) <= SECRET_KEY_DURATION;
    }

    private byte[] sendSecretKey(int serverId, SecretKey keyToSend) throws Exception {
        PublicKey serverKey = RSAKeyGenerator.readServerPublicKey(serverId);
        SecureMessage secureRequest = new SecureMessage(_serverId, keyToSend, serverKey, _keyPair.getPrivate());

        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + "/secret-key-server", request, SecureMessage.class);

        // Check response's signature and decipher TODO freshness
        assert secureResponse != null;
        return secureResponse.decipherAndVerify( keyToSend, serverKey);
    }

    public SecretKey getServerSecretKey(int serverId) throws Exception {

        if (!secretKeyValid(serverId)) {
            //System.out.print("Generating new secret key...");

            // Generate secret key
            SecretKey newSecretKey = AESKeyGenerator.makeAESKey();

            // Send key
            byte[] responseBytes = sendSecretKey(serverId, newSecretKey);

            // Check response
            if (responseBytes == null || !getStringFromBytes(responseBytes).equals("OK"))
                throw new IllegalArgumentException("Error exchanging new secret key");

            // Success! Update key
            saveServerSecretKey(serverId, newSecretKey);

            //System.out.println("Done!");
        }

        return _serverSecretKeys.get(serverId);
    }

    public SecretKey decipherAndVerifyKey(SecureMessage secureMessage, boolean serverMode) throws Exception {
        int senderId = secureMessage.get_senderId();
        PublicKey verifyKey;
        if (senderId == -1) verifyKey = getHAPublicKey();
        else if (serverMode) verifyKey = RSAKeyGenerator.readServerPublicKey(senderId);
        else verifyKey = RSAKeyGenerator.readClientPublicKey(senderId);
        return secureMessage.decipherAndVerifyKey( getPrivateKey(), verifyKey );
    }

    public SecureMessage cipherAndSignMessage(int receiverId, byte[] messageBytes, boolean serverMode) throws Exception {
        return new SecureMessage(_serverId, messageBytes, serverMode ? _serverSecretKeys.get(receiverId) : getClientSecretKey(receiverId), getPrivateKey());
    }

    public byte[] decipherAndVerifyMessage(SecureMessage secureMessage, boolean serverMode) throws Exception {
        int senderId = secureMessage.get_senderId();
        PublicKey verifyKey;
        if (senderId == -1) verifyKey = getHAPublicKey();
        else if (serverMode) verifyKey = RSAKeyGenerator.readServerPublicKey(senderId);
        else verifyKey = RSAKeyGenerator.readClientPublicKey(senderId);
        return secureMessage.decipherAndVerify( serverMode ? _serverSecretKeys.get(senderId) : getClientSecretKey(senderId), verifyKey );
    }

    public DBLocationReport decipherAndVerifyReport(SecureMessage secureMessage, boolean serverMode) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage, serverMode);
        LocationReport locationReport = LocationReport.getFromBytes(messageBytes);

        // check sender
        if (secureMessage.get_senderId() != locationReport.get_userId() && !serverMode)
            throw new ReportNotAcceptableException("Cannot submit reports from other users");

        // check proofs signatures
        int validProofCount = locationReport.verifyProofs();
        if (validProofCount <= BYZANTINE_USERS)
            throw new ReportNotAcceptableException("Not enough proofs to constitute an acceptable Location Report");

        return new DBLocationReport(locationReport, secureMessage.get_signature());
    }

    public DBLocationReport decipherAndVerifyDBReport(SecureMessage secureMessage, boolean serverMode) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage, serverMode);
        DBLocationReport dbLocationReport = DBLocationReport.getFromBytes(messageBytes);
        if (dbLocationReport == null) return null;

        // check corresponding report proofs signatures
        LocationReport locationReport = new LocationReport(dbLocationReport);
        int validProofCount = locationReport.verifyProofs();
        if (validProofCount <= BYZANTINE_USERS)
            throw new ReportNotAcceptableException("Not enough proofs to constitute an acceptable Location Report");

        return dbLocationReport;
    }

    public void checkObtainDBReportResponse(ObtainLocationRequest request, DBLocationReport response) {
        if (response == null) return;
        // Check content
        if (response.get_userId() != request.get_userId() || response.get_epoch() != request.get_epoch())
            throw new IllegalArgumentException("Bad server response!");
    }

    public ObtainLocationRequest decipherAndVerifyReportRequest(SecureMessage secureMessage, boolean serverMode) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage, serverMode);
        ObtainLocationRequest request = ObtainLocationRequest.getFromBytes(messageBytes);

        // check sender
        int sender_id = secureMessage.get_senderId();
        if ( !(sender_id == -1 || serverMode || sender_id == request.get_userId()) ) // if not from HA or user itself
            throw new IllegalArgumentException("Cannot request reports from other users");

        return request;
    }

    public WitnessProofsRequest decipherAndVerifyProofsRequest(SecureMessage secureMessage, boolean serverMode) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage, serverMode);
        WitnessProofsRequest request = WitnessProofsRequest.getFromBytes(messageBytes);

        // check sender
        if (secureMessage.get_senderId() != request.get_userId() && !serverMode)
            throw new IllegalArgumentException("Cannot request proofs from other users");

        return request;
    }

    public ObtainUsersRequest decipherAndVerifyUsersRequest(SecureMessage secureMessage, boolean serverMode) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage, serverMode);
        ObtainUsersRequest request = ObtainUsersRequest.getFromBytes(messageBytes);

        // check sender
        if (secureMessage.get_senderId() != -1 && !serverMode)
            throw new IllegalArgumentException("Only the health authority can make 'users at location' requests");

        return request;
    }

    /* ========================================================== */
    /* ====[               Regular Registers                ]==== */
    /* ========================================================== */

    private SecureMessage postToServer(int serverId, byte[] messageBytes, String endpoint) throws Exception {
        SecretKey secretKey = getServerSecretKey(serverId);
        SecureMessage secureRequest = new SecureMessage(_serverId, messageBytes, secretKey, _keyPair.getPrivate());
        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + endpoint, request, SecureMessage.class);
        serverSecretKeyUsed(serverId);
        return secureResponse;
    }

    public void broadcastWrite(DBLocationReport locationReport) throws Exception {
        boolean success = false;
        int acks = 0;
        int my_ts = locationReport.get_timestamp() + 1;
        locationReport.set_timestamp(my_ts);
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes = objectMapper.writeValueAsBytes(locationReport);

        System.out.println("Broadcasting write...");
        for (int serverId = 0; serverId < _serverCount; serverId++) {
            try {
                SecureMessage secureResponse = postToServer(serverId, bytes, "/broadcast-write");
                byte[] responseBytes = decipherAndVerifyMessage(secureResponse, true);
                if (responseBytes != null && getIntFromBytes(responseBytes).equals(my_ts)) {
                    //System.out.println("Acknowledged!");
                    acks += 1;
                }
                if (acks > (_serverCount+FAULTS)/2) success = true; //FIXME servercount+f?
            } catch(IllegalArgumentException e) {
                throw e;
            } catch(Exception ignored) {
                System.out.println(ignored.getMessage());
            } // We don't care if some servers fail, we only need (N+f)/2 to succeed
        }

        if (!success) throw new Exception("Write operation broadcast was unsuccessful");
        // FIXME : Dar rebroadcast?
    }

    public DBLocationReport broadcastRead(ObtainLocationRequest request) throws Exception {

        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes = objectMapper.writeValueAsBytes(request);
        ArrayList<DBLocationReport> readList = new ArrayList<>();

        System.out.println("Broadcasting read...");
        for (int serverId = 0; serverId < _serverCount; serverId++) {
            try{
                SecureMessage secureResponse = postToServer(serverId, bytes, "/broadcast-read");
                DBLocationReport locationReport = decipherAndVerifyDBReport(secureResponse, true);
                checkObtainDBReportResponse(request, locationReport);
                readList.add(locationReport);
                if (readList.size() > (_serverCount+FAULTS)/2) break;
            } catch(IllegalArgumentException e) {
                throw e;
            } catch(Exception ignored) {
                System.out.println(ignored.getMessage());
            }
        }

        if (readList.size() <= (_serverCount+FAULTS)/2) throw new Exception("Read operation broadcast was unsuccessful");
        // FIXME : Dar rebroadcast?


        // Choose the report with the largest timestamp
        DBLocationReport finalLocationReport = readList.get(0);
        for (DBLocationReport locationReport : readList){
            if (locationReport == null) continue;
            if (finalLocationReport == null || locationReport.get_timestamp() > finalLocationReport.get_timestamp())
                finalLocationReport = locationReport;
        }

        // Atomic Register: Write-back phase after Read
        if (finalLocationReport != null)
            broadcastWrite(finalLocationReport);

        return finalLocationReport;

    }

}
