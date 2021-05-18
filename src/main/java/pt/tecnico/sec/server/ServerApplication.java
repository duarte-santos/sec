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
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static pt.tecnico.sec.Constants.*;

@SpringBootApplication
public class ServerApplication {

    private static KeyPair _keyPair;
    private static int _serverId;
    private static int _serverCount;
    private static int _userCount;

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
        System.out.println("Changed key for " + id);
        _secretKeys.put(id, secretKey);
        if (id >= 1000) _secretKeysUsed.put(id, false); // keep usage count for server keys
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
        _secretKeysUsed.put(id, true); // mark key as used
    }

    public int getServerCount() {
        return _serverCount;
    }


    /* ========================================================== */
    /* ====[               Handle Secret Keys               ]==== */
    /* ========================================================== */

    private byte[] sendSecretKey(int serverId, SecretKey keyToSend) throws Exception {
        PublicKey serverKey = RSAKeyGenerator.readServerPublicKey(serverId);
        SecureMessage secureRequest = new SecureMessage(_serverId+1000, keyToSend, serverKey, _keyPair.getPrivate());

        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + "/secret-key", request, SecureMessage.class);

        // Check response's signature and decipher
        // TODO : freshness
        assert secureResponse != null;
        return secureResponse.decipherAndVerify( keyToSend, serverKey);
    }

    private void sendRefreshSecretKeys(int serverId) throws Exception {
        _restTemplate.getForObject(getServerURL(serverId) + "/refresh-secret-keys", void.class);
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
            if (responseBytes == null || !ObjectMapperHandler.getStringFromBytes(responseBytes).equals("OK"))
                throw new IllegalArgumentException("Error exchanging new secret key");

            // Success! Update key
            saveSecretKey(serverId+1000, newSecretKey);

            // Tell other servers to refresh keys
            sendRefreshSecretKeys(serverId);
        }
    }


    /* ========================================================== */
    /* ====[              Double Echo Broadcast             ]==== */
    /* ========================================================== */

    private int _broadcastId = 0;

    public void postToServer(int serverId, byte[] messageBytes, String endpoint) throws Exception {
        SecureMessage secureRequest = cipherAndSignMessage(serverId+1000, messageBytes);
        serverSecretKeyUsed(serverId+1000);
        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        _restTemplate.postForObject(getServerURL(serverId) + endpoint, request, SecureMessage.class);
    }

    public void postToServers(byte[] m, String endpoint) {
        for (int serverId = 0; serverId < _serverCount; serverId++) {
            AsyncPost thread = new AsyncPost(serverId, m, endpoint);
            thread.start();
        }
    }

    /* ====[                   W R I T E                    ]==== */

    private final Map<BroadcastId, BroadcastServiceW> _broadcastServicesW = new LinkedHashMap<>();
    private BroadcastServiceW _myBroadcastW = null;

    public BroadcastServiceW newBroadcastW(BroadcastId id) {
        BroadcastServiceW b = new BroadcastServiceW(this, id);
        _broadcastServicesW.put(id, b);
        if (_broadcastServicesW.size() > BROADCAST_SERVICES_MAX) cleanBroadcastServicesW();
        return b;
    }

    public BroadcastServiceW getBroadcastServiceW(BroadcastId id) {
        BroadcastServiceW b = _broadcastServicesW.get(id);
        if (b == null) // create new
            b = newBroadcastW(id);
        return b;
    }

    public void cleanBroadcastServicesW() {
        List<BroadcastId> toRemove = new ArrayList<>();
        Iterator<Map.Entry<BroadcastId, BroadcastServiceW>> iterator = _broadcastServicesW.entrySet().iterator();
        for (int i = 0; i < BROADCAST_SERVICES_MAX/2; i++) { // clean half of them, if they are delivered
            BroadcastId key = iterator.next().getKey();
            if (_broadcastServicesW.get(key).is_delivered())
                toRemove.add(key);
        }
        for (BroadcastId key : toRemove) _broadcastServicesW.remove(key);
    }

    public void broadcastW(DBLocationReport locationReport) throws Exception {
        _myBroadcastW = new BroadcastServiceW(this, new BroadcastId(_serverId+1000, _broadcastId));
        _myBroadcastW.broadcastSend(locationReport);
        _broadcastId++;
        _myBroadcastW = null;
    }

    public void broadcastDeliverW(int senderId, int timestamp) {
        // FIXME response should have the broadcast id as well?
        if (_myBroadcastW == null || !_myBroadcastW.validResponse(timestamp)) return; // drop if response was not asked for
        _myBroadcastW.broadcastDeliver(senderId, timestamp);
    }


    /* ====[                    R E A D                     ]==== */

    private final Map<BroadcastId, BroadcastServiceR> _broadcastServicesR = new LinkedHashMap<>();
    private BroadcastServiceR _myBroadcastR = null;

    public BroadcastServiceR newBroadcastR(BroadcastId id) {
        BroadcastServiceR b = new BroadcastServiceR(this, id);
        _broadcastServicesR.put(id, b);
        if (_broadcastServicesR.size() > BROADCAST_SERVICES_MAX) cleanBroadcastServicesR();
        return b;
    }

    public BroadcastServiceR getBroadcastServiceR(BroadcastId id) {
        BroadcastServiceR b = _broadcastServicesR.get(id);
        if (b == null) // create new
            b = newBroadcastR(id);
        return b;
    }

    public void cleanBroadcastServicesR() {
        List<BroadcastId> toRemove = new ArrayList<>();
        Iterator<Map.Entry<BroadcastId, BroadcastServiceR>> iterator = _broadcastServicesR.entrySet().iterator();
        for (int i = 0; i < BROADCAST_SERVICES_MAX/2; i++) { // clean half of them, if they are delivered
            BroadcastId key = iterator.next().getKey();
            if (_broadcastServicesR.get(key).is_delivered())
                toRemove.add(key);
        }
        for (BroadcastId key : toRemove) _broadcastServicesR.remove(key);
    }

    public DBLocationReport broadcastR(ObtainLocationRequest locationRequest) throws Exception {
        _myBroadcastR = new BroadcastServiceR(this, new BroadcastId(_serverId+1000, _broadcastId));
        DBLocationReport locationReport = _myBroadcastR.broadcastSend(locationRequest);
        _broadcastId++;
        _myBroadcastR = null;

        // Atomic Register: Write-back phase after Read
        if (locationReport != null) broadcastW(locationReport); // FIXME : only sender or all?

        return locationReport;
    }

    public void broadcastDeliverR(int senderId, DBLocationReport report) {
        // FIXME response should have the broadcast id as well?
        if (_myBroadcastR == null || !_myBroadcastR.validResponse(report)) return; // drop if response was not asked for
        _myBroadcastR.broadcastDeliver(senderId, report);
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
                ServerApplication.this.postToServer(_serverId, _messageBytes, _endpoint);
            } catch (Exception e) { // FIXME : ignore exceptions that are not IllegalArgument
                System.out.println(e.getMessage());
            }
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

    public SecretKey decipherAndVerifyKey(SecureMessage secureMessage) throws Exception {
        int senderId = secureMessage.get_senderId();
        PublicKey verifyKey = getVerifyKey(senderId);
        return secureMessage.decipherAndVerifyKey( getPrivateKey(), verifyKey );
    }

    public static SecureMessage cipherAndSignMessage(int receiverId, byte[] messageBytes) throws Exception {
        return new SecureMessage(_serverId + 1000, messageBytes, _secretKeys.get(receiverId), getPrivateKey());
    }

    public BroadcastWrite decipherAndVerifyBroadcastWrite(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        BroadcastWrite bw = ObjectMapperHandler.getBroadcastWriteFromBytes(messageBytes);

        // check sender
        if (!fromServer(secureMessage.get_senderId()))
            throw new ReportNotAcceptableException("Can only accept echos from servers.");

        return bw;
    }

    public DBLocationReport verifyBroadcastRequestW(BroadcastWrite bw) throws Exception {
        DBLocationReport dbLocationReport = bw.get_report();
        if (dbLocationReport == null) return null;

        // check sender
        if (!fromServer(bw.get_originalId()))
            throw new ReportNotAcceptableException("Can only accept register writes from servers.");

        // check corresponding report proofs signatures
        checkReportSignatures(new LocationReport(dbLocationReport));

        return dbLocationReport;
    }

    public int decipherAndVerifyDeliverW(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        int timestamp = ObjectMapperHandler.getIntFromBytes(messageBytes);

        // check sender
        if (!fromServer(secureMessage.get_senderId()))
            throw new ReportNotAcceptableException("Can only accept delivers from servers.");

        return timestamp;
    }

    public BroadcastRead decipherAndVerifyBroadcastRead(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        BroadcastRead br = ObjectMapperHandler.getBroadcastReadFromBytes(messageBytes);

        // check sender
        if (!fromServer(secureMessage.get_senderId()))
            throw new ReportNotAcceptableException("Can only accept echos from servers.");

        return br;
    }

    public ObtainLocationRequest verifyBroadcastRequestR(BroadcastRead br) {
        ObtainLocationRequest locationRequest = br.get_locationRequest();

        // check sender
        if (!fromServer(br.get_originalId()))
            throw new ReportNotAcceptableException("Can only accept register reads from servers.");

        return locationRequest;
    }

    public DBLocationReport decipherAndVerifyDeliverR(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        DBLocationReport report = ObjectMapperHandler.getDBLocationReportFromBytes(messageBytes);

        // check sender
        if (!fromServer(secureMessage.get_senderId()))
            throw new ReportNotAcceptableException("Can only accept delivers from servers.");

        return report;
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
