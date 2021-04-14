package pt.tecnico.sec.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.client.LocationReport;
import pt.tecnico.sec.client.SecureLocationReport;

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
    /* ====[             Receive Location Report            ]==== */
    /* ========================================================== */

    public LocationReport decipherAndVerifyReport(SecureLocationReport secureLocationReport) throws Exception {
        // decipher report
        PrivateKey decipherKey = _keyPair.getPrivate();
        LocationReport locationReport = secureLocationReport.decipher(decipherKey);

        // check report signature
        PublicKey verifyKey = getClientPublicKey(locationReport.get_userId());
        locationReport.verify(secureLocationReport.get_signature(), verifyKey);

        // check proofs signatures
        locationReport.verifyProofs();

        return locationReport;
    }

}
