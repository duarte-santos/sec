package pt.tecnico.sec.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.client.SecureLocationReport;
import pt.tecnico.sec.client.SignedLocationProof;
import pt.tecnico.sec.client.SignedLocationReport;

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

    public SignedLocationReport decipherReport(SecureLocationReport secureReport) throws Exception {
        // Decipher secret key
        byte[] cipheredKey = secureReport.get_cipheredKey();
        SecretKey secretKey = RSAKeyGenerator.decryptSecretKey(cipheredKey, _keyPair.getPrivate());

        // Decipher report
        byte[] data = AESKeyGenerator.decrypt(secureReport.get_cipheredReport(), secretKey);
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(data, SignedLocationReport.class);
    }

    public void verifyReportSignatures(SignedLocationReport signedReport) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<SignedLocationProof> signedProofs = signedReport.get_proofs();
        for (SignedLocationProof signedProof : signedProofs) {
            byte[] data = objectMapper.writeValueAsBytes(signedProof.get_locationProof());
            String signature = signedProof.get_signature();
            PublicKey clientKey = getClientPublicKey(signedProof.get_witnessId());
            if (!RSAKeyGenerator.verify(data, signature, clientKey)) {
                throw new IllegalArgumentException("Proof signature failed. Bad client!"); //FIXME type of exception
            }
        }
    }

}
