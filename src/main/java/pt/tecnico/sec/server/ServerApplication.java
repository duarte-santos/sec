package pt.tecnico.sec.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.client.report.LocationReport;
import pt.tecnico.sec.contract.*;
import pt.tecnico.sec.contract.exception.ReportNotAcceptableException;
import pt.tecnico.sec.keys.AESKeyGenerator;
import pt.tecnico.sec.keys.CryptoRSA;
import pt.tecnico.sec.keys.JavaKeyStore;
import pt.tecnico.sec.server.broadcast.BroadcastId;
import pt.tecnico.sec.server.broadcast.BroadcastMessage;
import pt.tecnico.sec.server.broadcast.BroadcastService;
import pt.tecnico.sec.server.database.DBLocationReport;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
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

    public void saveSecretKey(int id, SecretKey secretKey) throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException {
        System.out.println("Changed key for " + id);
        String alias;
        if (id >= 1000) {
            alias = "server" + (id - 1000);
            _secretKeysUsed.put(id, false); // keep usage count for server keys
        }
        else {
            alias = "user" + id;
        }
        _keyStore.setAndStoreSecretKey(alias, secretKey);
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

    public SecureMessage cipherAndSignKeyResponse(int receiverId, Message message) throws Exception {
        PublicKey cipherKey = getPublicKey(receiverId);
        return new SecureMessage(_serverId + 1000, message, cipherKey, _keyStore.getPersonalPrivateKey());
    }

    public SecureMessage cipherAndSignKeyException(int receiverId, Exception exception) {
        try {
            Message m = new Message(0, exception);
            return cipherAndSignKeyResponse(receiverId, m);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public Message decipherAndVerifyMessage(SecureMessage secureMessage) throws Exception {
        if (secureMessage == null) throw new IllegalArgumentException("Cannot decipher null message.");
        int senderId = secureMessage.get_senderId();
        PublicKey verifyKey = getPublicKey(senderId);
        String alias = (senderId >= 1000) ? "server" + (senderId - 1000) : "user" + (senderId);
        SecretKey secret = _keyStore.getSecretKey(alias);

        Message message;
        // Check Proof of Work
        if (!fromServer(senderId) && !fromHA(senderId)) {
            byte[] messageBytes = secureMessage.decipherAndVerify(secret, verifyKey);
            messageBytes = checkProofOfWork(messageBytes);
            message = ObjectMapperHandler.getMessageFromBytes(messageBytes);
        }
        else message = secureMessage.decipherAndVerifyMessage(secret, verifyKey);
        return message;
    }

    public static SecureMessage cipherAndSignMessage(int receiverId, Message message) throws Exception {
        String alias = (receiverId >= 1000) ? "server" + (receiverId - 1000) : "user" + (receiverId);
        SecretKey secret = _keyStore.getSecretKey(alias);
        return new SecureMessage(_serverId + 1000, message, secret, _keyStore.getPersonalPrivateKey());
    }

    public DBLocationReport decipherAndVerifyReport(SecureMessage secureMessage) throws Exception {
        Message message = decipherAndVerifyMessage(secureMessage);
        SignedLocationReport signedLocationReport = message.retrieveSignedLocationReport();
        LocationReport locationReport = signedLocationReport.get_report();

        locationReport.checkSender(secureMessage.get_senderId());

        // check proofs signatures
        checkReportSignatures(locationReport);

        return new DBLocationReport(locationReport, signedLocationReport.get_signature());
    }

    public void verifyDBReport(DBLocationReport report) {
        try {
            LocationReport originalReport = new LocationReport(report);
            byte[] bytes = ObjectMapperHandler.writeValueAsBytes(originalReport);
            String sig = report.get_signature();
            PublicKey verifyKey = getPublicKey(report.get_userId());
            if (sig == null || !CryptoRSA.verify(bytes, sig, verifyKey))
                throw new IllegalArgumentException("Report signature verify failed!");
            checkReportSignatures(originalReport);
        } catch (Exception e) {
            throw new IllegalArgumentException("DBReport failed verification!");
        }
    }

    public BroadcastMessage decipherAndVerifyBroadcastMessage(SecureMessage secureMessage) throws Exception {
        if (secureMessage == null) throw new IllegalArgumentException("Cannot decipher null broadcast message.");
        int senderId = secureMessage.get_senderId();
        PublicKey verifyKey = getPublicKey(senderId);
        String alias = (senderId >= 1000) ? "server" + (senderId - 1000) : "user" + (senderId);
        SecretKey secret = _keyStore.getSecretKey(alias);
        BroadcastMessage message = secureMessage.decipherAndVerifyBroadcastMessage(secret, verifyKey);
        message.checkOrigin();
        return message;
    }

    public static SecureMessage cipherAndSignBroadcastMessage(int receiverId, BroadcastMessage message) throws Exception {
        String alias = (receiverId >= 1000) ? "server" + (receiverId - 1000) : "user" + (receiverId);
        SecretKey secret = _keyStore.getSecretKey(alias);
        return new SecureMessage(_serverId + 1000, message, secret, _keyStore.getPersonalPrivateKey());
    }

    public void handleBroadcastMessageResponse(Message response) throws Exception {
        // Check response
        response.throwIfException();
        if (!response.isOKString())
            throw new IllegalArgumentException("Error during broadcast.");
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
        report.verifyProofs( _keyStore.getAllUsersPublicKeys() );
        if (report.get_proofs().size() <= F_USERS)
            throw new ReportNotAcceptableException("Not enough proofs to constitute an acceptable Location Report");
    }


    /* ========================================================== */
    /* ====[           Server-server communication          ]==== */
    /* ========================================================== */

    public Message postToServer(int serverId, Message message, String endpoint) throws Exception {
        SecureMessage secureRequest = cipherAndSignMessage(serverId+1000, message);
        serverSecretKeyUsed(serverId+1000);
        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + endpoint, request, SecureMessage.class);
        return decipherAndVerifyMessage(secureResponse);
    }

    public Message postBroadcastToServer(int serverId, BroadcastMessage message, String endpoint) throws Exception {
        SecureMessage secureRequest = cipherAndSignBroadcastMessage(serverId+1000, message);
        serverSecretKeyUsed(serverId+1000);
        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + endpoint, request, SecureMessage.class);
        return decipherAndVerifyMessage(secureResponse);
    }

    public void postToServers(BroadcastMessage m, String endpoint) {
        for (int serverId = 0; serverId < _serverCount; serverId++) {
            AsyncPost thread = new AsyncPost(serverId, m, endpoint);
            thread.start();
        }
    }

    /* ===========[        Handle Secret Keys        ]=========== */

    private Message sendSecretKey(int serverId, SecretKey keyToSend) throws Exception {
        PublicKey serverKey = _keyStore.getPublicKey("server" + serverId);
        PrivateKey myKey = _keyStore.getPersonalPrivateKey();
        SecureMessage secureRequest = new SecureMessage(_serverId+1000, keyToSend, serverKey, myKey);

        HttpEntity<SecureMessage> httpRequest = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + "/secret-key", httpRequest, SecureMessage.class);

        // Check response's signature and decipher
        // TODO : freshness
        assert secureResponse != null;
        return secureResponse.decipherAndVerifyMessage( myKey, serverKey);
    }

    public void refreshServerSecretKeys() throws Exception {

        for (int serverId = 0; serverId < _serverCount; serverId++) {

            boolean exists = ( _secretKeysUsed.get(serverId+1000) != null );
            if (exists && !_secretKeysUsed.get(serverId+1000)) continue; // there is a fresh key

            // Generate secret key
            SecretKey newSecretKey = AESKeyGenerator.makeAESKey();

            // No need to send key for myself
            if (serverId == _serverId) {
                saveSecretKey(serverId+1000, newSecretKey);
                continue;
            }

            // Send key
            Message response = sendSecretKey(serverId, newSecretKey);

            // Check response
            response.throwIfException();
            if (!response.isOKString())
                throw new IllegalArgumentException("Error exchanging new secret key.");

            // Success! Update key
            saveSecretKey(serverId+1000, newSecretKey);

            // Tell other servers to refresh keys
            response = postToServer(serverId, new Message(0, null), "/refresh-secret-keys");
            if (!response.isOKString())
                throw new IllegalArgumentException("Error sending refresh keys request.");
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

    public void broadcastW(DBLocationReport report) {
        BroadcastId broadcastId = new BroadcastId(_serverId+1000, _broadcastCount);
        report.set_timestamp( report.get_timestamp() + 1 );
        BroadcastMessage m = new BroadcastMessage(broadcastId, report);

        _myBroadcastW = new BroadcastService(this, broadcastId);
        System.out.println("Broadcasting write...");
        _myBroadcastW.broadcastSend(m);
        _broadcastCount++;
        _myBroadcastW = null;
    }

    /* ====[                    R E A D                     ]==== */

    private BroadcastService _myBroadcastR = null;

    public DBLocationReport broadcastR(ObtainLocationRequest locationRequest) {
        BroadcastId broadcastId = new BroadcastId(_serverId+1000, _broadcastCount);
        BroadcastMessage m = new BroadcastMessage(broadcastId, locationRequest);
        _myBroadcastR = new BroadcastService(this, broadcastId);
        System.out.println("Broadcasting read...");
        _myBroadcastR.broadcastSend(m);
        _broadcastCount++;

        // Choose the report with the largest timestamp
        DBLocationReport finalLocationReport = null;
        for (BroadcastMessage deliver : _myBroadcastR.get_delivers()) {
            try {
                if (deliver == null || deliver.get_report() == null) continue;
                DBLocationReport report = deliver.get_report();
                verifyDBReport(report);
                if (finalLocationReport == null || report.get_timestamp() > finalLocationReport.get_timestamp())
                    finalLocationReport = report;
            } catch (IllegalArgumentException ignored) {} // ignore invalid responses
        }
        _myBroadcastR = null;

        return finalLocationReport;
    }

    public DBLocationReport atomicBroadcastR(ObtainLocationRequest locationRequest) {
        DBLocationReport locationReport = broadcastR(locationRequest);

        // Atomic Register: Write-back phase after Read
        if (locationReport != null)
            broadcastW(locationReport);

        return locationReport;
    }


    /* ====[                   A S Y N C                    ]==== */

    class AsyncPost extends Thread {

        private final int _serverId;
        private final BroadcastMessage _message;
        private final String _endpoint;

        public AsyncPost(int serverId, BroadcastMessage message, String endpoint) {
            _serverId = serverId;
            _message = message;
            _endpoint = endpoint;
        }

        public void run() {
            try {
                Message response = ServerApplication.this.postBroadcastToServer(_serverId, _message, _endpoint);
                handleBroadcastMessageResponse(response);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

    }

    /* ========================================================== */
    /* ====[                 Proof of Work                  ]==== */
    /* ========================================================== */

    public byte[] checkProofOfWork(byte[] message) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(message);

        // Check if the hash has n leading 0s
        for (int k=0; k < POW_N; k++)
            if (hash[k] != 0) throw new IllegalArgumentException("Incorrect Proof of Work");

        // Return message without the nonce
        System.out.println("Correct Proof of Work");
        return Arrays.copyOfRange(message, 0, message.length-POW_N+2);
    }
}
