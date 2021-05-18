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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

    public int getServerCount() {
        return _serverCount;
    }

    /* ========================================================== */
    /* ====[          Decipher and Verify Requests          ]==== */
    /* ========================================================== */

    public byte[] decipherAndVerifyMessage(SecureMessage secureMessage) throws Exception {
        int senderId = secureMessage.get_senderId();
        PublicKey verifyKey = getVerifyKey(senderId);
        // printKey(senderId);
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

    public static SecureMessage cipherAndSignMessage(int receiverId, byte[] messageBytes) throws Exception {
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
    /* ====[               Handle Secret Keys               ]==== */
    /* ========================================================== */

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


    /* ========================================================== */
    /* ====[              Double Echo Broadcast             ]==== */
    /* ========================================================== */

    public void postToServer(int serverId, byte[] messageBytes, String endpoint) throws Exception {
        SecureMessage secureRequest = cipherAndSignMessage(serverId+1000, messageBytes);
        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        System.out.println("Sending request to " + serverId + " (endpoint: " + endpoint + ")");
        _restTemplate.postForObject(getServerURL(serverId) + endpoint, request, SecureMessage.class);
    }

    public void postToServers(byte[] m, String endpoint) {
        for (int serverId = 0; serverId < _serverCount; serverId++) {
            AsyncPost thread = new AsyncPost(serverId, m, endpoint);
            thread.start();
        }
    }

    /* ====[                   W R I T E                    ]==== */

    private Map<BroadcastId, BroadcastService> _broadcastServices = new LinkedHashMap<>();
    private int _broadcastId = 0;
    private BroadcastService _myBroadcast = null;

    public BroadcastService newBroadcast(BroadcastId id) {
        BroadcastService b = new BroadcastService(this, id);
        _broadcastServices.put(id, b);
        if (_broadcastServices.size() > BROADCAST_SERVICES_MAX) cleanBroadcastServices();
        return b;
    }

    public BroadcastService getBroadcastService(BroadcastId id) {
        BroadcastService b = _broadcastServices.get(id);
        if (b == null) // create new
            b = newBroadcast(id);
        return b;
    }

    public void cleanBroadcastServices() {
        Iterator<Map.Entry<BroadcastId, BroadcastService>> iterator = _broadcastServices.entrySet().iterator();
        for (int i = 0; i < BROADCAST_SERVICES_MAX/2; i++) { // clean half of them, if they are delivered
            BroadcastId key = iterator.next().getKey();
            if (_broadcastServices.get(key).is_delivered())
                _broadcastServices.remove(key);
        }
    }

    public void broadcastWrite(DBLocationReport locationReport) throws Exception {
        _myBroadcast = new BroadcastService(this, new BroadcastId(_serverId+1000, _broadcastId));
        _myBroadcast.broadcastSend(locationReport);
        _broadcastId++;
        _myBroadcast = null;
    }

    public void broadcastDeliver(int senderId, int timestamp) {
        // FIXME response should have the broadcast id as well?
        if (_myBroadcast == null || !_myBroadcast.validResponse(timestamp)) return; // drop if response was not asked for
        _myBroadcast.broadcastDeliver(senderId, timestamp);
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


    /* ====[                    R E A D                     ]==== */

    boolean _broadcasting_read = false;
    boolean _sentEcho_read = false;
    boolean _sentReady_read = false;
    boolean _delivered_read = false;
    BroadcastRead[] _echos_read = new BroadcastRead[_serverCount];
    BroadcastRead[] _readys_read = new BroadcastRead[_serverCount];
    Map<Integer, DBLocationReport> _readlist = new HashMap<>(_serverCount);


    public void setup_broadcast_read() {
        _broadcasting_read = true;
        _sentEcho_read = false;
        _sentReady_read = false;
        _delivered_read = false;
        _echos_read = new BroadcastRead[_serverCount];
        _readys_read = new BroadcastRead[_serverCount];
    }

    // Argument: ObtainLocationRequest
    public DBLocationReport doubleEchoBroadcastRead(ObtainLocationRequest locationRequest) throws Exception {
        // setup broadcast
        _readlist = new HashMap<>(_serverCount);
        setup_broadcast_read();

        BroadcastRead br = new BroadcastRead(_serverId + 1000, locationRequest);
        byte[] m = ObjectMapperHandler.writeValueAsBytes(br);

        System.out.println("Broadcasting read...");
        postToServers(m, "/doubleEchoBroadcast-send-read");

        // FIXME : ignore exceptions that are not IllegalArgument
        while (_readlist.size() <= (_serverCount + FAULTS) / 2) {
           // empty
        }
        System.out.println("> IḾ OUT, TIME TO PARTY!!!");

        // Choose the report with the largest timestamp
        DBLocationReport finalLocationReport = null;
        for (DBLocationReport locationReport : _readlist.values()) {
            if (locationReport == null) continue;
            if (finalLocationReport == null || locationReport.get_timestamp() > finalLocationReport.get_timestamp())
                finalLocationReport = locationReport;
        }

        // Atomic Register: Write-back phase after Read
        if (finalLocationReport != null)
            broadcastWrite(finalLocationReport); // FIXME : only sender or all?

        return finalLocationReport;
    }

    public void doubleEchoBroadcastSendDeliver_read(BroadcastRead br) throws Exception {
        System.out.println("> Broadcasting: " + _broadcasting_read + "\n> SentEcho: " + _sentEcho_read);
        TimeUnit.SECONDS.sleep(1); // FIXME : no zz for you, right now, sorry :(
        if (!_broadcasting_read) setup_broadcast_read();
        if (!_sentEcho_read) {
            byte[] m = ObjectMapperHandler.writeValueAsBytes(br);
            _sentEcho_read = true;
            postToServers(m, "/doubleEchoBroadcast-echo-read");
        }
    }

    // Argument: original SecureMessage
    public BroadcastRead searchForMajorityMessage_read(BroadcastRead[] messages, int quorum) {
        // count the times a message appears
        Map<BroadcastRead, Integer> counter = new HashMap<>();
        for (BroadcastRead m : messages) {
            if (m == null) continue;
            Integer count = counter.get(m);
            if (count == null)
                counter.put(m, 1);
            else
                counter.replace(m, count+1);
        }

        // search for message that appears more than <quorum> times
        for (BroadcastRead m : counter.keySet()) {
            if (counter.get(m) > quorum) return m;
        }
        return null;
    }

    public void doubleEchoBroadcastEchoDeliver_read(int senderId, BroadcastRead br) throws Exception {
        if (!_broadcasting_read) setup_broadcast_read();
        if (_echos_read[senderId] == null) _echos_read[senderId] = br;

        if (!_sentReady_read) {
            BroadcastRead message = searchForMajorityMessage_read(_echos_read, (_serverCount+FAULTS)/2);
            if (message != null) {
                _sentReady_read = true;
                byte[] m = ObjectMapperHandler.writeValueAsBytes(message);
                postToServers(m, "/doubleEchoBroadcast-ready-read");
            }
        }
    }

    // FIXME : can it receive only this message? careful with _broadcasting
    public boolean doubleEchoBroadcastReadyDeliver_read(int senderId, BroadcastRead br) throws Exception {
        if (_readys_read[senderId] == null) _readys_read[senderId] = br;

        if (!_sentReady_read) {
            BroadcastRead message = searchForMajorityMessage_read(_readys_read, FAULTS); // FIXME : f is faults or byzantines?
            if (message != null) {
                _sentReady_read = true;
                byte[] m = ObjectMapperHandler.writeValueAsBytes(message);
                postToServers(m, "/doubleEchoBroadcast-ready-read");
            }
            else
                return false; // for efficiency
        }

        if (!_delivered_read) {
            BroadcastRead message = searchForMajorityMessage_read(_readys_read, 2*FAULTS); // FIXME : f is faults or byzantines?
            if (message != null) {
                _delivered_read = true;
                _broadcasting_read = false;
                return true;
            }
        }
        return false;
    }

    public void doubleEchoBroadcastDeliver_read(int senderId, DBLocationReport report) {
        if (!_readlist.containsKey(senderId)) _readlist.put(senderId, report);
    }

    public BroadcastRead decipherAndVerifyBroadcastRead(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        BroadcastRead br = ObjectMapperHandler.getBroadcastReadFromBytes(messageBytes);

        // check sender
        if (!fromServer(secureMessage.get_senderId()))
            throw new ReportNotAcceptableException("Can only accept echos from servers.");

        return br;
    }

    public ObtainLocationRequest verifyBroadcastRead(BroadcastRead br) throws Exception {
        ObtainLocationRequest locationRequest = br.get_locationRequest();

        // check sender
        if (!fromServer(br.get_originalId()))
            throw new ReportNotAcceptableException("Can only accept register writes from servers.");

        return locationRequest;
    }

    public DBLocationReport decipherAndVerifyServerDeliver_read(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        DBLocationReport report = ObjectMapperHandler.getDBLocationReportFromBytes(messageBytes);

        // check sender
        if (!fromServer(secureMessage.get_senderId()))
            throw new ReportNotAcceptableException("Can only accept delivers from servers.");

        return report;
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

}
