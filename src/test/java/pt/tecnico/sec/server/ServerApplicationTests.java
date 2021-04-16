package pt.tecnico.sec.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;
import org.json.simple.parser.ParseException;
import org.junit.Before;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.EnvironmentGenerator;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.client.*;


import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpRequest;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class ServerApplicationTests {

    private MockMvc mockMvc;
    private Environment environment;
    private int epoch;
    private User user0;
    private User user1;
    private User user2;
    private KeyPair keyPair0;
    private KeyPair keyPair1;
    private KeyPair keyPair2;
    private PublicKey serverKey;

    @Mock
    static ServerApplication serverApp;

    @Mock
    static ReportRepository reportRepository;

    @InjectMocks
    static ServerController serverController;


    @BeforeEach
    public void setup() throws IOException, ParseException, GeneralSecurityException {

        serverApp.fetchRSAKeyPair();
        // create server mock
        this.mockMvc = standaloneSetup(serverController).build();

        // create environment
        environment = EnvironmentGenerator.parseEnvironmentJSON(); // import from randomly generated JSON
        List<Integer> userIds = environment.getUserList();
        System.out.println("Valid IDs: " + userIds);
        epoch = 0;

        // get keys
        String keysPath = RSAKeyGenerator.KEYS_PATH;
        serverKey = RSAKeyGenerator.readPublicKey(keysPath + "server.pub");
        keyPair0 = RSAKeyGenerator.readKeyPair(keysPath + 0 + ".pub", keysPath + 0 + ".priv");
        keyPair1 = RSAKeyGenerator.readKeyPair(keysPath + 1 + ".pub", keysPath + 1 + ".priv");
        keyPair2 = RSAKeyGenerator.readKeyPair(keysPath + 2 + ".pub", keysPath + 2 + ".priv");

        // create users
        user0 = new User(environment.getGrid(epoch), 0, keyPair0, serverKey);
        user1 = new User(environment.getGrid(epoch), 1, keyPair1, serverKey);
        user2 = new User(environment.getGrid(epoch), 2, keyPair2, serverKey);
    }

    @Test
    public void submitCorrectLocationReport() throws Exception {
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
        SecureMessage message = secureMessage(report, keyPair0);

        System.out.println(report);

        // Submit report

        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("http://localhost:9000/location-report");

        StringEntity entity = new StringEntity(asJsonString(message));
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept","application/json");
        httpPost.setHeader("Content-type", "application/json");

        CloseableHttpResponse response = client.execute(httpPost);
        System.out.println(response.getStatusLine().getStatusCode());

        // Obtain report

        ObtainLocationRequest locationRequest = new ObtainLocationRequest(0, epoch);
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes = objectMapper.writeValueAsBytes(locationRequest);
        SecretKey secretKey = AESKeyGenerator.makeAESKey();
        SecureMessage secureRequest = new SecureMessage(bytes, secretKey, serverKey, keyPair0.getPrivate());


        HttpPost httpPost2 = new HttpPost("http://localhost:9000/obtain-location-report");
        StringEntity entity2 = new StringEntity(asJsonString(secureRequest));
        httpPost2.setEntity(entity2);
        httpPost2.setHeader("Accept","application/json");
        httpPost2.setHeader("Content-type", "application/json");
        CloseableHttpResponse response2 = client.execute(httpPost2);
        JSONObject object = new JSONObject(new BasicResponseHandler().handleResponse(response2));

        ObjectMapper m = new ObjectMapper();
        SecureMessage secure = m.readValue(object.toString(), SecureMessage.class);

        String s = (String) object.get("_cipheredMessage");
        // Decipher and check signature
        byte[] messageBytes = secure.decipherAndVerify(keyPair0.getPrivate(), serverKey);
        LocationReport locationReport = LocationReport.getFromBytes(messageBytes);

        assert(locationReport.get_userId() == 0);

    }

    public SecureMessage secureMessage(LocationReport report, KeyPair keyPair) throws Exception {
        // encrypt using server public key, sign using client private key
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes = objectMapper.writeValueAsBytes(report);
        SecureMessage secureLocationReport = new SecureMessage(bytes, serverKey, keyPair.getPrivate());

        return secureLocationReport;
    }

    public static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
