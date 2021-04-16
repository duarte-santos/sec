package pt.tecnico.sec.server;

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
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.EnvironmentGenerator;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.client.*;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class ServerApplicationTests {

    private Environment environment;
    private User user0;
    private User user1;
    private User user2;
    private User user3;
    private KeyPair keyPair0;
    private KeyPair keyPair1;
    private KeyPair keyPair2;
    private KeyPair keyPair3;
    private PublicKey serverKey;


    @BeforeAll
    static void generate() throws Exception {
        EnvironmentGenerator.writeEnvironmentJSON(4, 4, 4, 4);
        RSAKeyGenerator.writeKeyPairs(4);
    }

    @BeforeEach
    public void setup() throws IOException, ParseException, GeneralSecurityException {

        // create environment
        environment = EnvironmentGenerator.parseEnvironmentJSON(); // import from randomly generated JSON

        // get keys
        String keysPath = RSAKeyGenerator.KEYS_PATH;
        serverKey = RSAKeyGenerator.readPublicKey(keysPath + "server.pub");
        keyPair0 = RSAKeyGenerator.readKeyPair(keysPath + 0 + ".pub", keysPath + 0 + ".priv");
        keyPair1 = RSAKeyGenerator.readKeyPair(keysPath + 1 + ".pub", keysPath + 1 + ".priv");
        keyPair2 = RSAKeyGenerator.readKeyPair(keysPath + 2 + ".pub", keysPath + 2 + ".priv");
        keyPair3 = RSAKeyGenerator.readKeyPair(keysPath + 3 + ".pub", keysPath + 2 + ".priv");

        // create users
        user0 = new User(environment.getGrid(0), 0, keyPair0, serverKey);
        user1 = new User(environment.getGrid(0), 1, keyPair1, serverKey);
        user2 = new User(environment.getGrid(0), 2, keyPair2, serverKey);
        user3 = new User(environment.getGrid(0), 3, keyPair3, serverKey);
    }

    @AfterAll
    static void cleanup() {
        // reset database
        JDBCExample.dropDatabase();
        JDBCExample.createDatabase();
    }

    @Test
    public void submitAndObtainCorrectLocationReport() throws Exception {
        // given a correct location report
        Location location = new Location(1, 1);
        ProofData proofData1 = new ProofData(location, 0, 1, 0, "success");
        ProofData proofData2 = new ProofData(location, 0, 2, 0, "success");
        LocationProof proof1 = user1.signLocationProof(proofData1);
        LocationProof proof2 = user2.signLocationProof(proofData2);
        List<LocationProof> proofList = new ArrayList<LocationProof>();
        proofList.add(proof1);
        proofList.add(proof2);
        LocationReport report = new LocationReport(0, 0, location, proofList);

        // Submit report
        submitReport(report);

        // Obtain report
        LocationReport r = obtainReport(0, 0);

        assert(r.get_userId() == 0);

    }

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


    public SecureMessage secureMessage(LocationReport report, KeyPair keyPair) throws Exception {
        // encrypt using server public key, sign using client private key
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes = objectMapper.writeValueAsBytes(report);
        SecureMessage secureLocationReport = new SecureMessage(bytes, serverKey, keyPair.getPrivate());

        return secureLocationReport;
    }

    public String submitReport(LocationReport r) throws Exception {
        SecureMessage message = secureMessage(r, keyPair0);

        System.out.println(r);
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("http://localhost:9000/location-report");

        StringEntity entity = new StringEntity(asJsonString(message));
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept","application/json");
        httpPost.setHeader("Content-type", "application/json");

        CloseableHttpResponse response2 = client.execute(httpPost);
        return new String(response2.getEntity().getContent().readAllBytes());
    }

    public LocationReport obtainReport(int userId, int epoch) throws Exception {
        ObtainLocationRequest locationRequest = new ObtainLocationRequest(userId, epoch);
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes = objectMapper.writeValueAsBytes(locationRequest);
        SecretKey secretKey = AESKeyGenerator.makeAESKey();
        SecureMessage secureRequest = new SecureMessage(bytes, secretKey, serverKey, keyPair0.getPrivate());

        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost2 = new HttpPost("http://localhost:9000/obtain-location-report");
        StringEntity entity2 = new StringEntity(asJsonString(secureRequest));
        httpPost2.setEntity(entity2);
        httpPost2.setHeader("Accept","application/json");
        httpPost2.setHeader("Content-type", "application/json");
        CloseableHttpResponse response2 = client.execute(httpPost2);
        String responseString = new String(response2.getEntity().getContent().readAllBytes());
        System.out.println(responseString);
        if (responseString.length() == 0) return null;

        JSONObject object = new JSONObject(responseString);

        ObjectMapper m = new ObjectMapper();
        SecureMessage secure = m.readValue(object.toString(), SecureMessage.class);

        // Decipher and check signature
        byte[] messageBytes = secure.decipherAndVerify(keyPair0.getPrivate(), serverKey);
        return LocationReport.getFromBytes(messageBytes);
    }

    public static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
