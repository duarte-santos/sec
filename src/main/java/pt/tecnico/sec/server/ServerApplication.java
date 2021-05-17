package pt.tecnico.sec.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.Constants;
import pt.tecnico.sec.ObjectMapperHandler;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.client.LocationReport;
import pt.tecnico.sec.client.ObtainLocationRequest;
import pt.tecnico.sec.client.SecureMessage;
import pt.tecnico.sec.client.WitnessProofsRequest;
import pt.tecnico.sec.healthauthority.ObtainUsersRequest;
import pt.tecnico.sec.server.exception.ReportNotAcceptableException;

import javax.crypto.SecretKey;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static javax.xml.bind.DatatypeConverter.printHexBinary;
import static pt.tecnico.sec.Constants.*;

@SpringBootApplication
public class ServerApplication {

    private static KeyPair _keyPair;
    private static int _serverId;
    private static int _serverCount;
    private static int _userCount;

    public final RestTemplate _restTemplate = new RestTemplate();

    private static final Map<Integer, SecretKey> _secretKeys = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> _secretKeysUsages = new ConcurrentHashMap<>();

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

    public SecureMessage postToServer(int serverId, byte[] messageBytes, String endpoint) throws Exception {
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
        System.out.println("SENDING KEY...");
        PublicKey serverKey = RSAKeyGenerator.readServerPublicKey(serverId);
        SecureMessage secureRequest = new SecureMessage(_serverId+1000, keyToSend, serverKey, _keyPair.getPrivate());

        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + "/secret-key", request, SecureMessage.class);

        // Check response's signature and decipher
        // TODO : freshness
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
        System.out.println("[*] decipherAndVerifyMessage");
        printKey(senderId);
        return secureMessage.decipherAndVerify( _secretKeys.get(senderId), verifyKey );
    }

    public ObtainLocationRequest decipherAndVerifyReportRequest(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        ObtainLocationRequest request = ObtainLocationRequest.getFromBytes(messageBytes);

        // check sender
        int sender_id = secureMessage.get_senderId();
        if ( !(fromHA(sender_id) || fromServer(sender_id) || fromSelf(sender_id, request.get_userId())) )
            throw new IllegalArgumentException("Cannot request reports from other users.");

        return request;
    }

    public DBLocationReport decipherAndVerifyReport(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
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

    /* ========================================================== */
    /* ====[              Double Echo Broadcast             ]==== */
    /* ========================================================== */

    public void printKey(int receiverId) {
        System.out.print("Key:");
        System.out.println(printHexBinary(_secretKeys.get(receiverId).getEncoded()));
        System.out.print("Usage:");
        System.out.println(_secretKeysUsages.get(receiverId));
    }

    public void voidPostToServer(int serverId, byte[] messageBytes, String endpoint) throws Exception {
        SecureMessage secureRequest = cipherAndSignMessage(serverId+1000, messageBytes);
        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        System.out.println("Sending request to " + serverId + " (endpoint: " + endpoint + ")");
        printKey(serverId+1000);
        _restTemplate.postForObject(getServerURL(serverId) + endpoint, request, SecureMessage.class);
    }

    private void postToServers(byte[] m, String endpoint) throws Exception {
        for (int serverId = 0; serverId < _serverCount; serverId++) {
            AsyncPost thread = new AsyncPost(serverId, m, endpoint);
            thread.start();
        }
    }

    boolean _broadcasting = false;
    boolean _sentEcho = false;
    boolean _sentReady = false;
    boolean _delivered = false;
    BroadcastWrite[] _echos = new BroadcastWrite[_serverCount];
    BroadcastWrite[] _readys = new BroadcastWrite[_serverCount];
    Integer[] _delivers = new Integer[_serverCount];


    public void setup_broadcast() {
        _broadcasting = true;
        _sentEcho = false;
        _sentReady = false;
        _delivered = false;
        _echos = new BroadcastWrite[_serverCount];
        _readys = new BroadcastWrite[_serverCount];
    }

    private void sendRefreshSecretKeys(int serverId) throws Exception {
        _restTemplate.getForObject(getServerURL(serverId) + "/refresh-secret-keys", void.class);
    }

    public void refreshServerSecretKeys() throws Exception {
        for (int serverId = 0; serverId < _serverCount; serverId++) {
            if (_secretKeysUsages.get(serverId+1000) != null && _secretKeysUsages.get(serverId+1000) == 0) continue;

            // Generate secret key
            SecretKey newSecretKey = AESKeyGenerator.makeAESKey();

            // Send key
            byte[] responseBytes = sendSecretKey(serverId, newSecretKey);

            // Check response
            if (responseBytes == null || !ObjectMapperHandler.getStringFromBytes(responseBytes).equals("OK"))
                throw new IllegalArgumentException("Error exchanging new secret key");

            // Success! Update key
            saveSecretKey(serverId+1000, newSecretKey);

            // Tell other servers to refresh keys
            sendRefreshSecretKeys(serverId);
        }
    }

    // Argument: Location report
    public void doubleEchoBroadcastWrite(DBLocationReport locationReport) throws Exception { // TODO : implement read
        // setup broadcast
        int acks = 0;
        _delivers = new Integer[_serverCount];
        setup_broadcast();

        int my_ts = locationReport.get_timestamp() + 1;
        locationReport.set_timestamp(my_ts);
        BroadcastWrite bw = new BroadcastWrite(_serverId + 1000, locationReport);
        byte[] m = ObjectMapperHandler.writeValueAsBytes(bw);

        refreshServerSecretKeys();

        System.out.println("Broadcasting write...");
        postToServers(m, "/doubleEchoBroadcast-send");
        // FIXME : ignore exceptions that are not IllegalArgument
        while (acks <= (_serverCount + FAULTS) / 2) {
            acks = 0;
            for (Integer timestamp : _delivers)
                if (timestamp != null && timestamp == my_ts) acks++;
        }
    }

    public void doubleEchoBroadcastSendDeliver(BroadcastWrite bw) throws Exception {
        TimeUnit.SECONDS.sleep(1); // FIXME : no zz for you, right now, sorry :(
        if (!_broadcasting) setup_broadcast();
        if (!_sentEcho) {
            byte[] m = ObjectMapperHandler.writeValueAsBytes(bw);
            _sentEcho = true;
            postToServers(m, "/doubleEchoBroadcast-echo");
        }
    }

    // Argument: original SecureMessage
    public BroadcastWrite searchForMajorityMessage(BroadcastWrite[] messages, int quorum) {
        // count the times a message appears
        Map<BroadcastWrite, Integer> counter = new HashMap<>();
        for (BroadcastWrite m : messages) {
            if (m == null) continue;
            Integer count = counter.get(m);
            if (count == null) counter.put(m, 1);
            else counter.replace(m, count+1);
        }

        // search for message that appears more than <quorum> times
        for (BroadcastWrite m : counter.keySet()) {
            if (counter.get(m) > quorum) return m;
        }
        return null;
    }

    public void doubleEchoBroadcastEchoDeliver(int senderId, BroadcastWrite bw) throws Exception {
        if (!_broadcasting) setup_broadcast();
        if (_echos[senderId] == null) _echos[senderId] = bw;

        if (!_sentReady) {
            BroadcastWrite message = searchForMajorityMessage(_echos, (_serverCount+FAULTS)/2);
            if (message != null) {
                _sentReady = true;
                byte[] m = ObjectMapperHandler.writeValueAsBytes(message);
                postToServers(m, "/doubleEchoBroadcast-ready");
            }
        }
    }

    // FIXME : can it receive only this message? careful with _broadcasting
    public boolean doubleEchoBroadcastReadyDeliver(int senderId, BroadcastWrite bw) throws Exception {
        if (_readys[senderId] == null) _readys[senderId] = bw;

        if (!_sentReady) {
            BroadcastWrite message = searchForMajorityMessage(_readys, FAULTS); // FIXME : f is faults or byzantines?
            if (message != null) {
                _sentReady = true;
                byte[] m = ObjectMapperHandler.writeValueAsBytes(message);
                postToServers(m, "/doubleEchoBroadcast-ready");
            }
            else return false; // for efficiency
        }

        if (!_delivered) {
            BroadcastWrite message = searchForMajorityMessage(_readys, 2*FAULTS); // FIXME : f is faults or byzantines?
            if (message != null) {
                _delivered = true;
                return true;
            }
        }
        return false;
    }

    public void doubleEchoBroadcastDeliver(int senderId, int timestamp) {
        if (_delivers[senderId] == null) _delivers[senderId] = timestamp;
        _broadcasting = false;
    }

    public BroadcastWrite decipherAndVerifyBroadcastWrite(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        BroadcastWrite bw = ObjectMapperHandler.getBroadcastWriteFromBytes(messageBytes);

        // check sender
        if (!fromServer(secureMessage.get_senderId()))
            throw new ReportNotAcceptableException("Can only accept echos from servers.");

        return bw;
    }

    public int decipherAndVerifyServerDeliver(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        int timestamp = ObjectMapperHandler.getIntFromBytes(messageBytes);

        // check sender
        if (!fromServer(secureMessage.get_senderId()))
            throw new ReportNotAcceptableException("Can only accept delivers from servers.");

        return timestamp;
    }

    public DBLocationReport verifyBroadcastWrite(BroadcastWrite bw) throws Exception {
        DBLocationReport dbLocationReport = bw.get_report();
        if (dbLocationReport == null) return null;

        // check sender
        if (!fromServer(bw.get_originalId()))
            throw new ReportNotAcceptableException("Can only accept register writes from servers.");

        // check corresponding report proofs signatures
        checkReportSignatures(new LocationReport(dbLocationReport));

        return dbLocationReport;
    }

    class AsyncPost extends Thread {

        private final int _serverId;
        private final byte[] _messageBytes;
        private final String _endpoint;

        public AsyncPost(int serverId, byte[] messageBytes, String endpoint) {
            _serverId = serverId;
            _messageBytes = messageBytes;
            _endpoint = endpoint;
        }

        public void run() {
            try {
                ServerApplication.this.voidPostToServer(_serverId, _messageBytes, _endpoint);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

    }

}
