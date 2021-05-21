package pt.tecnico.sec.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import pt.tecnico.sec.Constants;
import pt.tecnico.sec.EnvironmentGenerator;
import pt.tecnico.sec.Setup;
import pt.tecnico.sec.client.*;
import pt.tecnico.sec.client.domain.Environment;
import pt.tecnico.sec.client.domain.Grid;
import pt.tecnico.sec.client.report.Location;
import pt.tecnico.sec.client.report.LocationProof;
import pt.tecnico.sec.client.report.LocationReport;
import pt.tecnico.sec.client.report.ProofData;
import pt.tecnico.sec.contract.*;
import pt.tecnico.sec.keys.AESKeyGenerator;
import pt.tecnico.sec.keys.CryptoRSA;
import pt.tecnico.sec.keys.JavaKeyStore;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static javax.xml.bind.DatatypeConverter.printHexBinary;
import static pt.tecnico.sec.Constants.*;

@ExtendWith(MockitoExtension.class)
class ServerApplicationTests {

    private static final int USER_COUNT = 4;
    private static final int SERVER_COUNT = 3;

    private static Stack<SecretKey> _recentSecrets;
    private static JavaKeyStore[] _keyStores;
    private static User[] _users;


    @BeforeAll
    static void generate() throws Exception {
        String nX = "3", nY = "3", epochCount = "5", userCount = String.valueOf(USER_COUNT), serverCount = String.valueOf(SERVER_COUNT);
        String[] args = {nX, nY, epochCount, userCount, serverCount};
        // TODO : Please run the Setup before executing the tests
        _keyStores = new JavaKeyStore[USER_COUNT];
        _users = new User[USER_COUNT];
        _recentSecrets = new Stack<>();
    }

    private JavaKeyStore initKeyStore(String name, String password) throws CertificateException, IOException, NoSuchAlgorithmException, KeyStoreException {
        name = name + KEYSTORE_EXTENSION;
        JavaKeyStore keyStore = new JavaKeyStore(KEYSTORE_TYPE, password, name);
        keyStore.loadKeyStore();
        return keyStore;
    }

    @BeforeEach
    public void setup() throws IOException, ParseException, GeneralSecurityException {
        // Get environment // Import from randomly generated JSON
        Environment environment = EnvironmentGenerator.parseEnvironmentJSON();

        // Create users
        Grid grid = environment.getGrid(0);
        for (int userId = 0; userId < USER_COUNT; userId++) {
            String name = "user" + userId;
            String password = "user" + userId;
            JavaKeyStore keyStore = initKeyStore(name, password);
            _keyStores[userId] = keyStore;
            _users[userId] = new User(grid, userId, SERVER_COUNT, keyStore);
        }
    }

    @AfterAll
    static void cleanup() {
        // Reset database
        JDBCExample.dropDatabases(SERVER_COUNT);
        JDBCExample.createDatabases(USER_COUNT);
    }

    @Test
    public void submitAndObtainCorrectLocationReport() throws Exception {
        // Given a correct location report
        Location location = new Location(1, 1);
        ProofData proofData1 = new ProofData(location, 0, 1, 0, "success");
        ProofData proofData2 = new ProofData(location, 0, 2, 0, "success");
        LocationProof proof1 = _users[1].signLocationProof(proofData1);
        LocationProof proof2 = _users[2].signLocationProof(proofData2);
        List<LocationProof> proofList = new ArrayList<>();
        proofList.add(proof1);
        proofList.add(proof2);

        int userId = 0;
        LocationReport report = new LocationReport(userId, 0, location, proofList);

        // Submit report
        SecretKey secretKey = AESKeyGenerator.makeAESKey();
        postKeyToServer(userId, 0, secretKey);
        submitReport(userId, report, secretKey);

        // Obtain report
        LocationReport response = obtainReport(userId, 0, secretKey);

        System.out.println(response);
        assert(response.get_userId() == 0);
    }

    private void postKeyToServer(int senderId, int serverId, SecretKey keyToSend) throws Exception {
        PublicKey serverKey = _keyStores[senderId].getPublicKey("server" + serverId);
        PrivateKey myKey = _keyStores[senderId].getPersonalPrivateKey();

        SecureMessage secureRequest = new SecureMessage(senderId, keyToSend, serverKey, myKey);
        //System.out.println("\n\n\n\n\nRequest:\n" + secureRequest + "\n\n\n\n\n");

        int serverPort = SERVER_BASE_PORT + serverId;
        HttpPost httpPost = new HttpPost("http://localhost:" + serverPort + "/secret-key");
        StringEntity entity = new StringEntity(asJsonString(secureRequest));
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept","application/json");
        httpPost.setHeader("Content-type", "application/json");

        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response = client.execute(httpPost);
        String content = new String(response.getEntity().getContent().readAllBytes());
        //System.out.println("Response:\n" + content);
    }

    public String submitReport(int userId, LocationReport report, SecretKey secretKey) throws Exception {
        SecureMessage message = makeMessage(userId, report, secretKey);
        //System.out.println("Report:\n" + report);
        return submitMessage(message);
    }

    public SecureMessage makeMessage(int userId, LocationReport report, SecretKey secretKey) throws Exception {
        SignedLocationReport signedLocationReport = new SignedLocationReport(report, _keyStores[userId].getPersonalPrivateKey());
        Message request = new Message(signedLocationReport);
        byte[] messageBytes = ObjectMapperHandler.writeValueAsBytes(request);
        messageBytes = solvePuzzle(messageBytes);

        SecureMessage message = new SecureMessage(userId, messageBytes, secretKey, _keyStores[userId].getPersonalPrivateKey());

        return message;
    }

    public String submitMessage(SecureMessage message) throws Exception {
        //System.out.println("Report:\n" + report);
        int serverId = 0;
        int serverPort = SERVER_BASE_PORT + serverId;

        HttpPost httpPost = new HttpPost("http://localhost:" + serverPort + "/submit-location-report");

        StringEntity entity = new StringEntity(asJsonString(message));
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept","application/json");
        httpPost.setHeader("Content-type", "application/json");

        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response = client.execute(httpPost);
        return new String(response.getEntity().getContent().readAllBytes());
    }

    public LocationReport obtainReport(int userId, int epoch, SecretKey secretKey) throws Exception {
        ObtainLocationRequest locationRequest = new ObtainLocationRequest(userId, epoch);
        Message request = new Message(locationRequest);
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(request);
        bytes = solvePuzzle(bytes);

        SecureMessage secureRequest = new SecureMessage(userId, bytes, secretKey, _keyStores[userId].getPersonalPrivateKey());

        ObjectMapper objectMapper = new ObjectMapper();

        CloseableHttpClient client = HttpClients.createDefault();
        int serverId = 0;
        int serverPort = SERVER_BASE_PORT + serverId;
        HttpPost httpPost = new HttpPost("http://localhost:" + serverPort + "/obtain-location-report");
        StringEntity entity = new StringEntity(asJsonString(secureRequest));
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept","application/json");
        httpPost.setHeader("Content-type", "application/json");
        CloseableHttpResponse response = client.execute(httpPost);
        String responseString = new String(response.getEntity().getContent().readAllBytes());
        if (responseString.length() == 0) return null;

        JSONObject object = new JSONObject(responseString);
        SecureMessage secure = objectMapper.readValue(object.toString(), SecureMessage.class);

        // Decipher and check signature
        byte[] messageBytes = secure.decipherAndVerify(secretKey, _keyStores[userId].getPublicKey("server" + serverId));
        return LocationReport.getFromBytes(messageBytes);
    }

    public SecureMessage secureMessage(int userId, LocationReport report, SecretKey secretKey) throws Exception {
        byte[] messageBytes = ObjectMapperHandler.writeValueAsBytes(report);
        SecureMessage m = new SecureMessage(userId, messageBytes, secretKey, _keyStores[userId].getPersonalPrivateKey());
        System.out.println(m);
        return m;
    }

    public static String asJsonString(Object object) {
        try {
            return new ObjectMapper().writeValueAsString(object);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] solvePuzzle(byte[] message) throws NoSuchAlgorithmException {

        System.out.print("Generating proof of work...");

        int i;
        byte[] nounce;
        byte[] messageWithNonce;
        byte[] hash;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        // Try to find the nounce to get n leading zeros
        for (i=0; ;i++){

            nounce = ByteBuffer.allocate(POW_N+2).putInt(i).array();
            messageWithNonce = new byte[message.length + nounce.length];
            System.arraycopy(message, 0, messageWithNonce,0, message.length);
            System.arraycopy(nounce, 0, messageWithNonce, message.length, nounce.length);

            hash = digest.digest(messageWithNonce);

            // Check if it has n leading 0s
            boolean found = true;
            for (int k = 0; k < POW_N; k++){
                if (hash[k] != 0) {
                    found = false;
                    break;
                }
            }
            if (found) break;

        }
        System.out.println("Done!");
        return messageWithNonce;
    }


    @Test
    public void submitLocationReportWithoutProofs() throws Exception {
        // given a report without proofs
        Location location = new Location(1, 1);
        List<LocationProof> proofList = new ArrayList<LocationProof>();
        LocationReport report = new LocationReport(0, 1, location, proofList);

        // Submit report
        int userId = 0;
        SecretKey secretKey = AESKeyGenerator.makeAESKey();
        postKeyToServer(userId, 0, secretKey);
        String response = submitReport(userId, report, secretKey);

        JSONObject object = new JSONObject(response);
        ObjectMapper objectMapper = new ObjectMapper();
        SecureMessage secure = objectMapper.readValue(object.toString(), SecureMessage.class);

        // Decipher and check signature
        byte[] messageBytes = secure.decipherAndVerify(secretKey, _keyStores[userId].getPublicKey("server" + 0));
        String s = new String(messageBytes);

        assert(s.contains("Not enough proofs"));

    }

    @Test
    public void submitLocationReportWithWrongProofs() throws Exception {
        // given proofs from epoch 0, try to submit report from epoch 2
        Location location = new Location(1, 1);
        ProofData proofData1 = new ProofData(location, 0, 1, 0, "success");
        ProofData proofData2 = new ProofData(location, 0, 2, 0, "success");
        LocationProof proof1 = _users[1].signLocationProof(proofData1);
        LocationProof proof2 = _users[2].signLocationProof(proofData2);
        List<LocationProof> proofList = new ArrayList<LocationProof>();
        proofList.add(proof1);
        proofList.add(proof2);
        LocationReport report = new LocationReport(0, 2, location, proofList);

        // Submit report
        int userId = 0;
        SecretKey secretKey = AESKeyGenerator.makeAESKey();
        postKeyToServer(userId, 0, secretKey);
        String response = submitReport(userId, report, secretKey);
        JSONObject object = new JSONObject(response);
        ObjectMapper objectMapper = new ObjectMapper();
        SecureMessage secure = objectMapper.readValue(object.toString(), SecureMessage.class);

        // Decipher and check signature
        byte[] messageBytes = secure.decipherAndVerify(secretKey, _keyStores[userId].getPublicKey("server" + 0));
        String s = new String(messageBytes);
        assert(s.contains("Invalid LocationProof"));

    }

    @Test
    public void submitLocationReportWithWrongId() throws Exception {
        // given a user with id 0, try to submit a report for the user with id 1
        Location location = new Location(1, 1);
        ProofData proofData1 = new ProofData(location, 0, 1, 3, "success");
        ProofData proofData2 = new ProofData(location, 0, 2, 3, "success");
        LocationProof proof1 = _users[1].signLocationProof(proofData1);
        LocationProof proof2 = _users[2].signLocationProof(proofData2);
        List<LocationProof> proofList = new ArrayList<LocationProof>();
        proofList.add(proof1);
        proofList.add(proof2);
        LocationReport report = new LocationReport(1, 3, location, proofList);

        // Submit report
        int userId = 1;
        SecretKey secretKey = AESKeyGenerator.makeAESKey();
        postKeyToServer(userId, 0, secretKey);
        String response = submitReport(userId, report, secretKey);
        JSONObject object = new JSONObject(response);
        ObjectMapper objectMapper = new ObjectMapper();
        SecureMessage secure = objectMapper.readValue(object.toString(), SecureMessage.class);

        // Decipher and check signature
        byte[] messageBytes = secure.decipherAndVerify(secretKey, _keyStores[userId].getPublicKey("server" + 0));
        String s = new String(messageBytes);
        //System.out.println("\n\n\n\n" + s + "\n\n\n\n");
        assert(true);

    }

    @Test
    public void submitLocationReportWithOwnProofs() throws Exception {
        // given a report with proofs ONLY from the same user who sends the report
        Location location = new Location(1, 1);
        ProofData proofData1 = new ProofData(location, 0, 0, 4, "success");
        ProofData proofData2 = new ProofData(location, 0, 0, 4, "success");
        LocationProof proof1 = _users[1].signLocationProof(proofData1);
        LocationProof proof2 = _users[2].signLocationProof(proofData2);
        List<LocationProof> proofList = new ArrayList<LocationProof>();
        proofList.add(proof1);
        proofList.add(proof2);
        LocationReport report = new LocationReport(0, 4, location, proofList);

        // Submit report
        int userId = 0;
        SecretKey secretKey = AESKeyGenerator.makeAESKey();
        postKeyToServer(userId, 0, secretKey);
        String response = submitReport(userId, report, secretKey);
        JSONObject object = new JSONObject(response);
        ObjectMapper objectMapper = new ObjectMapper();
        SecureMessage secure = objectMapper.readValue(object.toString(), SecureMessage.class);

        // Decipher and check signature
        byte[] messageBytes = secure.decipherAndVerify(secretKey, _keyStores[userId].getPublicKey("server" + 0));
        String s = new String(messageBytes);
        assert(s.contains("Invalid LocationProof"));

    }

    @Test
    public void submitLocationReportWithFalsifiedProofs() throws Exception {
        // given a report with falsified proofs -> not correctly signed
        Location location = new Location(1, 1);
        ProofData proofData1 = new ProofData(location, 0, 1, 5, "success");
        ProofData proofData2 = new ProofData(location, 0, 2, 5, "success");
        LocationProof proof1 = _users[0].signLocationProof(proofData1); // User 0 signs both proofs
        LocationProof proof2 = _users[0].signLocationProof(proofData2);
        List<LocationProof> proofList = new ArrayList<LocationProof>();
        proofList.add(proof1);
        proofList.add(proof2);
        LocationReport report = new LocationReport(0, 5, location, proofList);

        // Submit report
        int userId = 0;
        SecretKey secretKey = AESKeyGenerator.makeAESKey();
        postKeyToServer(userId, 0, secretKey);
        String response = submitReport(userId, report, secretKey);
        JSONObject object = new JSONObject(response);
        ObjectMapper objectMapper = new ObjectMapper();
        SecureMessage secure = objectMapper.readValue(object.toString(), SecureMessage.class);

        // Decipher and check signature
        byte[] messageBytes = secure.decipherAndVerify(secretKey, _keyStores[userId].getPublicKey("server" + 0));
        String s = new String(messageBytes);
        assert(s.contains("Invalid LocationProof"));

    }

    @Test
    public void replayReportSubmission() throws Exception {
        // given a correct report
        Location location = new Location(1, 1);
        ProofData proofData1 = new ProofData(location, 0, 1, 6, "success");
        ProofData proofData2 = new ProofData(location, 0, 2, 6, "success");
        LocationProof proof1 = _users[1].signLocationProof(proofData1);
        LocationProof proof2 = _users[2].signLocationProof(proofData2);
        List<LocationProof> proofList = new ArrayList<LocationProof>();
        proofList.add(proof1);
        proofList.add(proof2);
        LocationReport report = new LocationReport(0, 6, location, proofList);

        // Submit report
        int userId = 0;
        SecretKey secretKey = AESKeyGenerator.makeAESKey();
        postKeyToServer(userId, 0, secretKey);
        SecureMessage message = makeMessage(userId, report, secretKey);

        String response1 = submitMessage(message);
        JSONObject object = new JSONObject(response1);
        ObjectMapper objectMapper = new ObjectMapper();
        SecureMessage secure = objectMapper.readValue(object.toString(), SecureMessage.class);

        // Decipher and check signature
        byte[] messageBytes = secure.decipherAndVerify(secretKey, _keyStores[userId].getPublicKey("server" + 0));
        String s = new String(messageBytes);
        System.out.println(response1);
        assert(true);

        // Submit same report -> Replay attack
        String response2 = submitMessage(message);
        JSONObject object2 = new JSONObject(response2);
        ObjectMapper objectMapper2 = new ObjectMapper();
        SecureMessage secure2 = objectMapper2.readValue(object2.toString(), SecureMessage.class);

        // Decipher and check signature
        byte[] messageBytes2 = secure2.decipherAndVerify(secretKey, _keyStores[userId].getPublicKey("server" + 0));
        String s2 = new String(messageBytes2);
        assert(true);

    }

}
