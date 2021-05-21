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
import pt.tecnico.sec.contract.ObjectMapperHandler;
import pt.tecnico.sec.contract.ObtainLocationRequest;
import pt.tecnico.sec.contract.SecureMessage;
import pt.tecnico.sec.keys.AESKeyGenerator;
import pt.tecnico.sec.keys.CryptoRSA;
import pt.tecnico.sec.keys.JavaKeyStore;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static javax.xml.bind.DatatypeConverter.printHexBinary;
import static pt.tecnico.sec.Constants.*;

@ExtendWith(MockitoExtension.class)
class ServerApplicationTests {

    private static final int USER_COUNT = 3;
    private static final int SERVER_COUNT = 3;

    private static Stack<SecretKey> _recentSecrets;
    private static JavaKeyStore[] _keyStores;
    private static User[] _users;


    @BeforeAll
    static void generate() throws Exception {
        String nX = "3", nY = "3", epochCount = "5", userCount = String.valueOf(USER_COUNT), serverCount = String.valueOf(SERVER_COUNT);
        String[] args = {nX, nY, epochCount, userCount, serverCount};
        Setup.main(args);
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

        // FIXME : updateSecretKey

        // Submit report
        submitReport(userId, report);

        // Obtain report
        LocationReport response = obtainReport(userId, 0);

        assert(response.get_userId() == 0);
    }

    public String submitReport(int userId, LocationReport report) throws Exception {
        SecureMessage message = secureMessage(userId, report);

        System.out.println("Report:\n" + report);
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

    public LocationReport obtainReport(int userId, int epoch) throws Exception {
        ObtainLocationRequest locationRequest = new ObtainLocationRequest(userId, epoch);
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes = objectMapper.writeValueAsBytes(locationRequest);
        SecretKey secretKey = _recentSecrets.pop();

        System.out.println("\n\n\n\n" + printHexBinary(_keyStores[userId].getPersonalPrivateKey().getEncoded()) + "\n\n\n\n" + printHexBinary(secretKey.getEncoded()) + "\n\n\n\n");
        SecureMessage secureRequest = new SecureMessage(userId, bytes, secretKey, _keyStores[userId].getPersonalPrivateKey());

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
        System.out.println("Response:\n" + responseString);
        if (responseString.length() == 0) return null;

        JSONObject object = new JSONObject(responseString);
        SecureMessage secure = objectMapper.readValue(object.toString(), SecureMessage.class);

        // Decipher and check signature
        byte[] messageBytes = secure.decipherAndVerify(secretKey, _keyStores[userId].getPublicKey("server" + serverId));
        return LocationReport.getFromBytes(messageBytes);
    }

    public SecureMessage secureMessage(int userId, LocationReport report) throws Exception {
        byte[] messageBytes = ObjectMapperHandler.writeValueAsBytes(report);
        SecretKey secretKey = AESKeyGenerator.makeAESKey();
        _recentSecrets.push(secretKey);
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

/*
    @Test
    public void submitLocationReportWithByzantineProof() throws Exception {
        // given a location report from a correct user
        Location location = new Location(1, 1);
        ProofData proofData1 = new ProofData(location, 0, 1, 7, "success");
        ProofData proofData2 = new ProofData(location, 0, 2, 7, "success");
        // and an incorrect proof -> given by a Byzantine user
        ProofData proofData3 = new ProofData(location, 0, 3, 3, "refused");

        LocationProof proof1 = user1.signLocationProof(proofData1);
        LocationProof proof2 = user2.signLocationProof(proofData2);
        LocationProof proof3 = user3.signLocationProof(proofData3);
        List<LocationProof> proofList = new ArrayList<LocationProof>();
        proofList.add(proof1);
        proofList.add(proof2);
        proofList.add(proof3);
        LocationReport report = new LocationReport(0, 7, location, proofList);

        // Submit report
        submitReport(report);

        // Obtain report
        LocationReport r = obtainReport(0, 7);

        // the server only stores the correct proofs
        assert(r.get_proofs().size() == 2);

    }

    @Test
    public void submitLocationReportWithoutProofs() throws Exception {
        // given a report without proofs
        Location location = new Location(1, 1);
        List<LocationProof> proofList = new ArrayList<LocationProof>();
        LocationReport report = new LocationReport(0, 1, location, proofList);

        // Submit report
        String response = submitReport(report);
        assert(response.contains("NOT_ACCEPTABLE"));

    }

    @Test
    public void submitLocationReportWithWrongProofs() throws Exception {
        // given proofs from epoch 0, try to submit report from epoch 2
        Location location = new Location(1, 1);
        ProofData proofData1 = new ProofData(location, 0, 1, 0, "success");
        ProofData proofData2 = new ProofData(location, 0, 2, 0, "success");
        LocationProof proof1 = user1.signLocationProof(proofData1);
        LocationProof proof2 = user2.signLocationProof(proofData2);
        List<LocationProof> proofList = new ArrayList<LocationProof>();
        proofList.add(proof1);
        proofList.add(proof2);
        LocationReport report = new LocationReport(0, 2, location, proofList);

        // Submit report
        String response = submitReport(report);
        assert(response.contains("NOT_ACCEPTABLE"));

    }

    @Test
    public void submitLocationReportWithWrongId() throws Exception {
        // given a user with id 0, try to submit a report for the user with id 1
        Location location = new Location(1, 1);
        ProofData proofData1 = new ProofData(location, 0, 1, 3, "success");
        ProofData proofData2 = new ProofData(location, 0, 2, 3, "success");
        LocationProof proof1 = user1.signLocationProof(proofData1);
        LocationProof proof2 = user2.signLocationProof(proofData2);
        List<LocationProof> proofList = new ArrayList<LocationProof>();
        proofList.add(proof1);
        proofList.add(proof2);
        LocationReport report = new LocationReport(1, 3, location, proofList);

        // Submit report
        String response = submitReport(report);
        assert(response.contains("UNAUTHORIZED"));

    }

    @Test
    public void submitLocationReportWithOwnProofs() throws Exception {
        // given a report with proofs ONLY from the same user who sends the report
        Location location = new Location(1, 1);
        ProofData proofData1 = new ProofData(location, 0, 0, 4, "success");
        ProofData proofData2 = new ProofData(location, 0, 0, 4, "success");
        LocationProof proof1 = user1.signLocationProof(proofData1);
        LocationProof proof2 = user2.signLocationProof(proofData2);
        List<LocationProof> proofList = new ArrayList<LocationProof>();
        proofList.add(proof1);
        proofList.add(proof2);
        LocationReport report = new LocationReport(0, 4, location, proofList);

        // Submit report
        String response = submitReport(report);
        assert(response.contains("NOT_ACCEPTABLE"));

    }

    @Test
    public void submitLocationReportWithFalsifiedProofs() throws Exception {
        // given a report with falsified proofs -> not correctly signed
        Location location = new Location(1, 1);
        ProofData proofData1 = new ProofData(location, 0, 1, 5, "success");
        ProofData proofData2 = new ProofData(location, 0, 2, 5, "success");
        LocationProof proof1 = user0.signLocationProof(proofData1); // User 0 signs both proofs
        LocationProof proof2 = user0.signLocationProof(proofData2);
        List<LocationProof> proofList = new ArrayList<LocationProof>();
        proofList.add(proof1);
        proofList.add(proof2);
        LocationReport report = new LocationReport(0, 5, location, proofList);

        // Submit report
        String response = submitReport(report);
        assert(response.contains("NOT_ACCEPTABLE"));

    }

    @Test
    public void replayReportSubmission() throws Exception {
        // given a correct report
        Location location = new Location(1, 1);
        ProofData proofData1 = new ProofData(location, 0, 1, 6, "success");
        ProofData proofData2 = new ProofData(location, 0, 2, 6, "success");
        LocationProof proof1 = user1.signLocationProof(proofData1);
        LocationProof proof2 = user2.signLocationProof(proofData2);
        List<LocationProof> proofList = new ArrayList<LocationProof>();
        proofList.add(proof1);
        proofList.add(proof2);
        LocationReport report = new LocationReport(0, 6, location, proofList);

        // Submit first report
        String response1 = submitReport(report);
        System.out.println(response1);
        assert(!response1.contains("CONFLICT"));

        // Submit same report -> Replay attack
        String response2 = submitReport(report);
        System.out.println(response2);
        assert(response2.contains("CONFLICT"));

    }

*/
}
