package pt.tecnico.sec.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.client.LocationReport;
import pt.tecnico.sec.client.ObtainLocationRequest;
import pt.tecnico.sec.client.SecureMessage;
import pt.tecnico.sec.healthauthority.ObtainUsersRequest;
import pt.tecnico.sec.server.exception.ReportNotAcceptableException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collections;

@SpringBootApplication
public class ServerApplication {

    /* constants definition */
    private static final String USAGE = "Usage: ./mvnw spring-boot:run -\"Dstart-class=pt.tecnico.sec.server.ServerApplication";
    private static final int SERVER_PORT = 9000;
    private static final int BYZANTINE_USERS = 1;

    private static KeyPair _keyPair;

    public static void main(String[] args) {
        try {
            fetchRSAKeyPair();
            SpringApplication springApplication = new SpringApplication(ServerApplication.class);
            springApplication.setDefaultProperties(Collections.singletonMap("server.port", String.valueOf(SERVER_PORT)));
            springApplication.run(args);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(USAGE);
        }
    }


    /* ========================================================== */
    /* ====[              Auxiliary functions               ]==== */
    /* ========================================================== */

    public static void fetchRSAKeyPair() throws IOException, GeneralSecurityException {
        // get server's keyPair
        String keysPath = RSAKeyGenerator.KEYS_PATH + "server";
        _keyPair = RSAKeyGenerator.readKeyPair(keysPath + ".pub", keysPath + ".priv");
    }

    public static PrivateKey getPrivateKey() {
        return _keyPair.getPrivate();
    }

    public static PublicKey getHAPublicKey() throws GeneralSecurityException, IOException {
        String keyPath = RSAKeyGenerator.KEYS_PATH + "ha.pub";
        return RSAKeyGenerator.readPublicKey(keyPath);
    }

    public static PublicKey getClientPublicKey(int clientId) throws GeneralSecurityException, IOException {
        String keyPath = RSAKeyGenerator.KEYS_PATH + clientId + ".pub";
        return RSAKeyGenerator.readPublicKey(keyPath);
    }


    /* ========================================================== */
    /* ====[              Secure communication              ]==== */
    /* ========================================================== */

    public LocationReport decipherAndVerifyReport(SecureMessage secureMessage) throws ReportNotAcceptableException, Exception {
        // decipher report
        byte[] messageBytes = secureMessage.decipher( _keyPair.getPrivate() );
        LocationReport locationReport = LocationReport.getFromBytes(messageBytes);

        // check report signature
        PublicKey verifyKey = getClientPublicKey(locationReport.get_userId());
        secureMessage.verify(messageBytes, verifyKey);

        // check proofs signatures
        int validProofCount = locationReport.verifyProofs();
        if (validProofCount <= BYZANTINE_USERS)
            throw new ReportNotAcceptableException("Not enough proofs to constitute an acceptable Location Report");

        return locationReport;
    }

    public ObtainLocationRequest decipherAndVerifyRequest(SecureMessage secureMessage, boolean fromHA) throws Exception {
        // decipher request
        byte[] messageBytes = secureMessage.decipher( _keyPair.getPrivate() );
        ObtainLocationRequest request = ObtainLocationRequest.getFromBytes(messageBytes);

        // check report signature
        PublicKey verifyKey = fromHA ? getHAPublicKey() : getClientPublicKey(request.get_userId());
        secureMessage.verify(messageBytes, verifyKey);

        return request;
    }

    public ObtainUsersRequest decipherAndVerifyHAUsersRequest(SecureMessage secureMessage) throws Exception {
        // decipher request
        byte[] messageBytes = secureMessage.decipher( _keyPair.getPrivate() );
        ObtainUsersRequest request = ObtainUsersRequest.getFromBytes(messageBytes);

        // check report signature
        PublicKey verifyKey = getHAPublicKey();
        secureMessage.verify(messageBytes, verifyKey);

        return request;
    }


}
