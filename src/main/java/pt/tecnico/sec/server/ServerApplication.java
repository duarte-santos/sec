package pt.tecnico.sec.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.client.LocationProof;
import pt.tecnico.sec.client.LocationReport;
import pt.tecnico.sec.client.SecureLocationReport;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;

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

    public static PublicKey getClientPublicKey(int clientId) throws GeneralSecurityException, IOException {
        String keyPath = RSAKeyGenerator.KEYS_PATH + clientId + ".pub";
        return RSAKeyGenerator.readPublicKey(keyPath);
    }


    /* ========================================================== */
    /* ====[             Receive Location Report            ]==== */
    /* ========================================================== */

    public LocationReport decipherReport(SecureLocationReport secureReport) throws Exception {
        // Decipher secret key
        byte[] cipheredKey = secureReport.get_cipheredKey();
        SecretKey secretKey = RSAKeyGenerator.decryptSecretKey(cipheredKey, _keyPair.getPrivate());

        // Decipher report
        byte[] data = AESKeyGenerator.decrypt(secureReport.get_cipheredReport(), secretKey);
        ObjectMapper objectMapper = new ObjectMapper();
        LocationReport report = objectMapper.readValue(data, LocationReport.class);

        // Verify signature
        byte[] signature = secureReport.get_signature();
        PublicKey clientKey = getClientPublicKey(report.get_userId());
        if (signature == null || !RSAKeyGenerator.verify(data, signature, clientKey)) {
            throw new IllegalArgumentException("Report signature failed. Bad client!"); //FIXME type of exception
        }

        return report;
    }

    public void verifyReportSignatures(LocationReport locationReport) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<LocationProof> signedProofs = locationReport.get_proofs();
        for (LocationProof signedProof : signedProofs) {
            byte[] data = objectMapper.writeValueAsBytes(signedProof.get_proofData());
            byte[] signature = signedProof.get_signature();
            PublicKey clientKey = getClientPublicKey(signedProof.get_witnessId());
            if (signature == null || !RSAKeyGenerator.verify(data, signature, clientKey)) {
                throw new IllegalArgumentException("Proof signature failed. Bad client!"); //FIXME type of exception
            }
        }
    }


    /* ========================================================== */
    /* ====[              Send Location Report              ]==== */
    /* ========================================================== */

    public SecureLocationReport secureLocationReport(LocationReport report) throws Exception {
        // make secret key
        SecretKey secretKey = AESKeyGenerator.makeAESKey();

        // encrypt report with secret key
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] reportBytes = objectMapper.writeValueAsBytes(report);
        byte[] cipheredReport = AESKeyGenerator.encrypt(reportBytes, secretKey);

        // encrypt secret key with client public key
        PublicKey clientKey = getClientPublicKey(report.get_userId());
        byte[] cipheredSecretKey = RSAKeyGenerator.encryptSecretKey(secretKey, clientKey);

        // sign report with server private key
        byte[] signature = RSAKeyGenerator.sign(reportBytes, _keyPair.getPrivate());

        // build secure report
        return new SecureLocationReport(cipheredSecretKey, cipheredReport, signature);
    }

}
