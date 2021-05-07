package pt.tecnico.sec.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
import java.util.Map;


@SpringBootApplication
public class ServerApplication {

    /* constants definition */
    private static final String USAGE = "Usage: ./mvnw spring-boot:run -\"Dstart-class=pt.tecnico.sec.server.ServerApplication";
    private static final int BASE_PORT = 9000;
    private static final int BYZANTINE_USERS = 1;

    private static KeyPair _keyPair;
    private static int _serverId;

    private static Map<Integer, SecretKey> _secretKeys = new HashMap<>();

    public static void main(String[] args) {
        try {
            // get serverId to determine its port
            _serverId = Integer.parseInt(args[0]);
            // TODO : check if serverId is valid (ex: according to the serverCount)
            int serverPort = BASE_PORT + _serverId;

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
            System.out.println(USAGE);
        }
    }


    /* ========================================================== */
    /* ====[              Auxiliary functions               ]==== */
    /* ========================================================== */

    public static void fetchRSAKeyPair() throws IOException, GeneralSecurityException {
        // get server's keyPair
        String keysPath = RSAKeyGenerator.KEYS_PATH + "s" + _serverId;
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
        String keyPath = RSAKeyGenerator.KEYS_PATH + "ha.pub";
        return RSAKeyGenerator.readPublicKey(keyPath);
    }

    public SecretKey getSecretKey(int id) {
        return _secretKeys.get(id);
    }

    public void saveSecretKey(int id, SecretKey secretKey) {
        _secretKeys.put(id, secretKey);
    }


    /* ========================================================== */
    /* ====[              Secure communication              ]==== */
    /* ========================================================== */

    public SecretKey decipherAndVerifyKey(SecureMessage secureMessage) throws Exception {
        int senderId = secureMessage.get_senderId();
        PublicKey verifyKey = (senderId == -1) ? getHAPublicKey() : RSAKeyGenerator.readClientPublicKey(senderId);
        return secureMessage.decipherAndVerifyKey( getPrivateKey(), verifyKey );
    }

    public SecureMessage cipherAndSignMessage(int receiverId, byte[] messageBytes) throws Exception {
        return new SecureMessage(_serverId, messageBytes, getSecretKey(receiverId), getPrivateKey());
    }

    public byte[] decipherAndVerifyMessage(SecureMessage secureMessage) throws Exception {
        int senderId = secureMessage.get_senderId();
        PublicKey verifyKey = (senderId == -1) ? getHAPublicKey() : RSAKeyGenerator.readClientPublicKey(senderId);
        return secureMessage.decipherAndVerify( getSecretKey(senderId), verifyKey );
    }

    public DBLocationReport decipherAndVerifyReport(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        LocationReport locationReport = LocationReport.getFromBytes(messageBytes);

        // check sender
        if (secureMessage.get_senderId() != locationReport.get_userId())
            throw new ReportNotAcceptableException("Cannot submit reports from other users");

        // check proofs signatures
        int validProofCount = locationReport.verifyProofs();
        if (validProofCount <= BYZANTINE_USERS)
            throw new ReportNotAcceptableException("Not enough proofs to constitute an acceptable Location Report");

        return new DBLocationReport(locationReport, secureMessage.get_signature());
    }

    public ObtainLocationRequest decipherAndVerifyReportRequest(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        ObtainLocationRequest request = ObtainLocationRequest.getFromBytes(messageBytes);

        // check sender
        int sender_id = secureMessage.get_senderId();
        if ( !(sender_id == -1 || sender_id == request.get_userId()) ) // if not from HA or user itself
            throw new IllegalArgumentException("Cannot request reports from other users");

        return request;
    }

    public WitnessProofsRequest decipherAndVerifyProofsRequest(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        WitnessProofsRequest request = WitnessProofsRequest.getFromBytes(messageBytes);

        // check sender
        if (secureMessage.get_senderId() != request.get_userId())
            throw new IllegalArgumentException("Cannot request proofs from other users");

        return request;
    }

    public ObtainUsersRequest decipherAndVerifyUsersRequest(SecureMessage secureMessage) throws Exception {
        byte[] messageBytes = decipherAndVerifyMessage(secureMessage);
        ObtainUsersRequest request = ObtainUsersRequest.getFromBytes(messageBytes);

        // check sender
        if (secureMessage.get_senderId() != -1)
            throw new IllegalArgumentException("Only the health authority can make 'users at location' requests");

        return request;
    }

}
