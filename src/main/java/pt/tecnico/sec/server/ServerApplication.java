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


    /* ========================================================== */
    /* ====[              Secure communication              ]==== */
    /* ========================================================== */

    public DBLocationReport decipherAndVerifyReport(SecureMessage secureMessage) throws Exception {
        // decipher report
        byte[] messageBytes = secureMessage.decipher( getPrivateKey() );
        LocationReport locationReport = LocationReport.getFromBytes(messageBytes);

        // check report signature
        PublicKey verifyKey = RSAKeyGenerator.readClientPublicKey(locationReport.get_userId());
        secureMessage.verify(messageBytes, verifyKey);

        // check proofs signatures
        int validProofCount = locationReport.verifyProofs();
        if (validProofCount <= BYZANTINE_USERS)
            throw new ReportNotAcceptableException("Not enough proofs to constitute an acceptable Location Report");

        return new DBLocationReport(locationReport, secureMessage.get_signature());
    }

    public ObtainLocationRequest decipherAndVerifyLocationRequest(SecureMessage secureMessage, boolean fromHA) throws Exception {
        // decipher request
        byte[] messageBytes = secureMessage.decipher( getPrivateKey() );
        ObtainLocationRequest request = ObtainLocationRequest.getFromBytes(messageBytes);

        // check report signature
        PublicKey verifyKey = fromHA ? getHAPublicKey() : RSAKeyGenerator.readClientPublicKey(request.get_userId());
        secureMessage.verify(messageBytes, verifyKey);

        return request;
    }

    public WitnessProofsRequest decipherAndVerifyProofsRequest(SecureMessage secureMessage) throws Exception {
        // decipher request
        byte[] messageBytes = secureMessage.decipher( getPrivateKey() );
        WitnessProofsRequest request = WitnessProofsRequest.getFromBytes(messageBytes);

        // check report signature
        PublicKey verifyKey = RSAKeyGenerator.readClientPublicKey(request.get_userId());
        secureMessage.verify(messageBytes, verifyKey);

        return request;
    }

    public ObtainUsersRequest decipherAndVerifyHAUsersRequest(SecureMessage secureMessage) throws Exception {
        // decipher request
        byte[] messageBytes = secureMessage.decipher( getPrivateKey() );
        ObtainUsersRequest request = ObtainUsersRequest.getFromBytes(messageBytes);

        // check report signature
        PublicKey verifyKey = getHAPublicKey();
        secureMessage.verify(messageBytes, verifyKey);

        return request;
    }


}
