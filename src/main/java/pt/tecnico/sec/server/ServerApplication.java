package pt.tecnico.sec.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.JavaKeyStore;
import pt.tecnico.sec.ObjectMapperHandler;
import pt.tecnico.sec.client.LocationReport;
import pt.tecnico.sec.client.ObtainLocationRequest;
import pt.tecnico.sec.client.SecureMessage;
import pt.tecnico.sec.server.exception.ReportNotAcceptableException;

import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static pt.tecnico.sec.Constants.*;

@SpringBootApplication
public class ServerApplication {

    private static int _serverId;
    private static int _serverCount;
    private static int _userCount;
    private static JavaKeyStore _keyStore;

    public final RestTemplate _restTemplate = new RestTemplate();

    private static final Map<Integer, SecretKey> _secretKeys = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> _secretKeysUsed = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            // parse arguments
            _serverId = Integer.parseInt(args[0]);
            _serverCount = Integer.parseInt(args[1]);
            _userCount = Integer.parseInt(args[2]);

            if (_serverId >= _serverCount)
                throw new NumberFormatException("Server ID must be lower than the number of servers");

            // Instantiate KeyStore
            String keyStoreName = "server" + _serverId + KEYSTORE_EXTENSION;
            String keyStorePassword = "server" + _serverId;
            _keyStore = new JavaKeyStore(KEYSTORE_TYPE, keyStorePassword, keyStoreName);
            _keyStore.loadKeyStore();

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

    public static int getUserCount() {
        return _userCount;
    }

    public void saveSecretKey(int id, SecretKey secretKey) {
        System.out.println("Changed key for " + id);
        _secretKeys.put(id, secretKey);
        if (id >= 1000) _secretKeysUsed.put(id, false); // keep usage count for server keys
    }


    /* ========================================================== */
    /* ====[              Auxiliary functions               ]==== */
    /* ========================================================== */

    public String getServerURL(int serverId) {
        int serverPort = SERVER_BASE_PORT + serverId;
        return "http://localhost:" + serverPort;
    }

    public void serverSecretKeyUsed(int id) {
        assert id >= 1000;
        _secretKeysUsed.put(id, true); // mark key as used
    }

    public int getServerCount() {
        return _serverCount;
    }


    /* ========================================================== */
    /* ====[             Ciphers and Signatures             ]==== */
    /* ========================================================== */

    public SecretKey decipherAndVerifyKey(SecureMessage secureMessage) throws Exception {
        int senderId = secureMessage.get_senderId();
        PublicKey verifyKey = getPublicKey(senderId);
        return secureMessage.decipherAndVerifyKey(_keyStore.getPersonalPrivateKey(), verifyKey );
    }

    public SecureMessage cipherAndSignKeyResponse(int receiverId, byte[] messageBytes) throws Exception {
        PublicKey cipherKey = getPublicKey(receiverId);
        return new SecureMessage(_serverId + 1000, messageBytes, cipherKey, _keyStore.getPersonalPrivateKey());
    }

    public SecureMessage cipherAndSignKeyException(int receiverId, Exception exception) {
        try {
            byte[] messageBytes = ObjectMapperHandler.writeValueAsBytes(exception);
            return cipherAndSignKeyResponse(receiverId, messageBytes);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public byte[] decipherAndVerifyMessage(SecureMessage secureMessage) throws Exception {
        if (secureMessage == null) throw new IllegalArgumentException("Cannot decipher null message.");
        int senderId = secureMessage.get_senderId();
        PublicKey verifyKey = getPublicKey(senderId);
        return secureMessage.decipherAndVerify( _secretKeys.get(senderId), verifyKey );
    }

    public static SecureMessage cipherAndSignMessage(int receiverId, byte[] messageBytes) throws Exception {
        return new SecureMessage(_serverId + 1000, messageBytes, _secretKeys.get(receiverId), _keyStore.getPersonalPrivateKey());
    }

    public static SecureMessage cipherAndSignException(int receiverId, Exception exception) {
        try {
            byte[] messageBytes = ObjectMapperHandler.writeValueAsBytes(exception);
            return cipherAndSignMessage(receiverId, messageBytes);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public DBLocationReport decipherAndVerifyReport(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        LocationReport locationReport = LocationReport.getFromBytes(messageBytes);
        locationReport.checkSender(secureMessage.get_senderId());

        // check proofs signatures
        checkReportSignatures(locationReport);

        return new DBLocationReport(locationReport, secureMessage.get_signature());
    }

    public BroadcastMessage decipherAndVerifyBroadcastMessage(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        BroadcastMessage m = ObjectMapperHandler.getBroadcastMessageFromBytes(messageBytes);
        m.checkOrigin();
        return m;
    }

    public void handleBroadcastMessageResponse(byte[] bytes) throws Exception {
        if (!ObjectMapperHandler.isOKString(bytes)) {
            ObjectMapperHandler.throwIfException(bytes);
            throw new IllegalArgumentException("Error during broadcast.");
        }
    }

    /* ===========[   Auxiliary   ]=========== */

    public boolean fromServer(int senderId) {
        return senderId >= 1000;
    }

    public boolean fromHA(int senderId) {
        return senderId == -1;
    }

    public PublicKey getPublicKey(int senderId) throws GeneralSecurityException {
        if (fromHA(senderId)) {
            return _keyStore.getPublicKey("ha" + 0); // int haId = 0
        } else if (fromServer(senderId)) {
            int id = senderId - 1000;
            return (id != _serverId) ? _keyStore.getPublicKey("server" + id) : _keyStore.getPersonalPublicKey();
        } else {
            return _keyStore.getPublicKey("user" + senderId);
        }
    }

    public void checkReportSignatures(LocationReport report) throws Exception {
        List<PublicKey> clientKeys = _keyStore.getAllUsersPublicKeys();
        int validProofCount = report.verifyProofs(clientKeys);
        if (validProofCount <= BYZANTINE_USERS)
            throw new ReportNotAcceptableException("Not enough proofs to constitute an acceptable Location Report");
    }


    /* ========================================================== */
    /* ====[           Server-server communication          ]==== */
    /* ========================================================== */

    public byte[] postToServer(int serverId, byte[] messageBytes, String endpoint) throws Exception {
        SecureMessage secureRequest = cipherAndSignMessage(serverId+1000, messageBytes);
        serverSecretKeyUsed(serverId+1000);
        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + endpoint, request, SecureMessage.class);
        return decipherAndVerifyMessage(secureResponse);
    }

    public void postToServers(byte[] m, String endpoint) {
        for (int serverId = 0; serverId < _serverCount; serverId++) {
            AsyncPost thread = new AsyncPost(serverId, m, endpoint);
            thread.start();
        }
    }

    /* ===========[        Handle Secret Keys        ]=========== */

    private byte[] sendSecretKey(int serverId, SecretKey keyToSend) throws Exception {
        PublicKey serverKey = _keyStore.getPublicKey("server" + serverId);
        PrivateKey myKey = _keyStore.getPersonalPrivateKey();
        SecureMessage secureRequest = new SecureMessage(_serverId+1000, keyToSend, serverKey, myKey);

        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + "/secret-key", request, SecureMessage.class);

        // Check response's signature and decipher
        // TODO : freshness
        assert secureResponse != null;
        return secureResponse.decipherAndVerify( myKey, serverKey);
    }

    private void sendRefreshSecretKeys(int serverId) throws Exception {
        SecureMessage secureRequest = new SecureMessage();
        secureRequest.set_senderId(_serverId+1000);
        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + "/refresh-secret-keys", request, SecureMessage.class);
        byte[] responseBytes = decipherAndVerifyMessage(secureResponse);
        if (!ObjectMapperHandler.isOKString(responseBytes)) {
            ObjectMapperHandler.throwIfException(responseBytes);
            throw new IllegalArgumentException("Error during broadcast.");
        }
    }

    public void refreshServerSecretKeys() throws Exception {
        for (int serverId = 0; serverId < _serverCount; serverId++) {
            if (_secretKeys.get(serverId+1000) != null && !_secretKeysUsed.get(serverId+1000)) continue; // there is a fresh key

            // Generate secret key
            SecretKey newSecretKey = AESKeyGenerator.makeAESKey();

            // No need to send key for myself
            if (serverId == _serverId) {
                saveSecretKey(serverId+1000, newSecretKey);
                continue;
            }

            // Send key
            byte[] responseBytes = sendSecretKey(serverId, newSecretKey);

            // Check response
            if (!ObjectMapperHandler.isOKString(responseBytes)) {
                ObjectMapperHandler.throwIfException(responseBytes);
                throw new IllegalArgumentException("Error exchanging new secret key");
            }

            // Success! Update key
            saveSecretKey(serverId+1000, newSecretKey);

            // Tell other servers to refresh keys
            sendRefreshSecretKeys(serverId);
        }
    }


    /* ========================================================== */
    /* ====[              Double Echo Broadcast             ]==== */
    /* ========================================================== */

    private int _broadcastCount = 0;
    private final Map<BroadcastId, BroadcastService> _broadcastServices = new LinkedHashMap<>();

    public BroadcastService newBroadcast(BroadcastId id) {
        BroadcastService b = new BroadcastService(this, id);
        _broadcastServices.put(id, b);
        if (_broadcastServices.size() > BROADCAST_SERVICES_MAX) cleanBroadcastServices(_broadcastServices);
        return b;
    }

    public BroadcastService getBroadcastService(BroadcastId id) {
        BroadcastService b = _broadcastServices.get(id);
        if (b == null) // create new
            b = newBroadcast(id);
        return b;
    }

    private void cleanBroadcastServices(Map<BroadcastId, BroadcastService> broadcastServicesR) {
        List<BroadcastId> toRemove = new ArrayList<>();
        Iterator<Map.Entry<BroadcastId, BroadcastService>> iterator = broadcastServicesR.entrySet().iterator();
        for (int i = 0; i < BROADCAST_SERVICES_MAX/2; i++) { // clean half of them, if they are delivered
            BroadcastId key = iterator.next().getKey();
            if (broadcastServicesR.get(key).is_delivered())
                toRemove.add(key);
        }
        for (BroadcastId key : toRemove) broadcastServicesR.remove(key);
    }

    public void broadcastDeliver(int senderId, BroadcastMessage response){
        if (_myBroadcastW != null && _myBroadcastW.validResponse(response))
            _myBroadcastW.broadcastDeliver(senderId, response);
        else if (_myBroadcastR != null && _myBroadcastR.validResponse(response))
            _myBroadcastR.broadcastDeliver(senderId, response);
        // drop if response was not asked for
    }

    /* ====[                   W R I T E                    ]==== */

    private BroadcastService _myBroadcastW = null;

    public void broadcastW(DBLocationReport report) throws Exception {
        BroadcastId broadcastId = new BroadcastId(_serverId+1000, _broadcastCount);
        report.set_timestamp( report.get_timestamp() + 1 );
        BroadcastMessage m = new BroadcastMessage(broadcastId, report);

        _myBroadcastW = new BroadcastService(this, broadcastId);
        _myBroadcastW.broadcastSend(m);
        _broadcastCount++;
        _myBroadcastW = null;
    }

    /* ====[                    R E A D                     ]==== */

    private BroadcastService _myBroadcastR = null;

    public DBLocationReport broadcastR(ObtainLocationRequest locationRequest) throws Exception {
        BroadcastId broadcastId = new BroadcastId(_serverId+1000, _broadcastCount);
        BroadcastMessage m = new BroadcastMessage(broadcastId, locationRequest);
        _myBroadcastR = new BroadcastService(this, broadcastId);
        _myBroadcastR.broadcastSend(m);
        _broadcastCount++;

        // Choose the report with the largest timestamp
        DBLocationReport finalLocationReport = null;
        for (BroadcastMessage deliver : _myBroadcastR.get_delivers()) {
            if (deliver == null || deliver.get_report() == null) continue;
            DBLocationReport report = deliver.get_report();
            if (finalLocationReport == null || report.get_timestamp() > finalLocationReport.get_timestamp())
                finalLocationReport = report;
        }
        _myBroadcastR = null;

        // Atomic Register: Write-back phase after Read
        if (finalLocationReport != null) broadcastW(finalLocationReport); // FIXME : only sender or all?

        return finalLocationReport;
    }

    /* ====[                   A S Y N C                    ]==== */

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
                byte[] responseBytes = ServerApplication.this.postToServer(_serverId, _messageBytes, _endpoint);
                handleBroadcastMessageResponse(responseBytes);
            } catch (Exception e) {
                System.out.println(e.getMessage()); //TODO handle illegal argument exceptions
            }
        }

    }
}
