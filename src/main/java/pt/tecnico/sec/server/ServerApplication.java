package pt.tecnico.sec.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import pt.tecnico.sec.RSAKeyGenerator;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Collections;

@SpringBootApplication
public class ServerApplication {

    /* constants definition */
    private static final String USAGE = "Usage: ./mvnw spring-boot:run -\"Dstart-class=pt.tecnico.sec.server.ServerApplication";
    private static final int BASE_PORT = 9000;

    private static KeyPair _keyPair;

    public static void main(String[] args) {
        try {
            int port = BASE_PORT;
            fetchRSAKeyPair();
            SpringApplication springApplication = new SpringApplication(ServerApplication.class);
            springApplication.setDefaultProperties(Collections.singletonMap("server.port", String.valueOf(port)));
            springApplication.run(args);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(USAGE);
        }
    }

    public void reportLocation(LocationReport report){
        System.out.println(report.toString());
    }

    public static void fetchRSAKeyPair() throws IOException, GeneralSecurityException {
        // get server's keyPair
        String keysPath = RSAKeyGenerator.KEYS_PATH + "server";
        _keyPair = RSAKeyGenerator.readKeyPair(keysPath + ".pub", keysPath + ".priv");
    }

    public static PrivateKey getPrivateKey() {
        return _keyPair.getPrivate();
    }
}
