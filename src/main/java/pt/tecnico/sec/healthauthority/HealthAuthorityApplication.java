package pt.tecnico.sec.healthauthority;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.client.*;
import pt.tecnico.sec.server.exception.ReportNotAcceptableException;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Scanner;

import static java.lang.System.exit;

@SpringBootApplication
public class HealthAuthorityApplication {

    // constants
    private static final int HA_PORT     = 6000;
    private static final int SERVER_PORT = 9000;
    private static final String HELP     = """
            ======================== Available Commands ========================
            obtainLocationReport, [userId], [ep]
            > returns the position of "userId" at the epoch "ep"

            obtainUsersAtLocation, [x], [y], [ep]
            > returns a list of users that were at position (x,y) at epoch "ep"

            exit
            > exits the Health Authority application
            ====================================================================
            """;
    private static final int BYZANTINE_USERS = 1;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(HealthAuthorityApplication.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", HA_PORT));
        app.run(args);
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    public CommandLineRunner run(RestTemplate restTemplate) {
        return args -> {
            // get keys
            String keysPath = RSAKeyGenerator.KEYS_PATH;
            KeyPair keyPair = RSAKeyGenerator.readKeyPair(keysPath + "ha.pub", keysPath + "ha.priv");
            PublicKey serverKey = RSAKeyGenerator.readPublicKey(keysPath + "server.pub");

            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    try {
                        System.out.print("\n\n> Type your command ('help' to view available commands)\n> ");

                        // read command line and parse arguments
                        String line = scanner.nextLine();
                        String[] tokens = line.trim().split("\\s*,\\s*");

                        if (line.equals("exit")) {
                            break;
                        }

                        else if (line.equals("help")) {
                            System.out.println(HELP);
                        }

                        // obtainLocationReport, [userId], [ep]
                        // Specification: returns the position of "userId" at the epoch "ep"
                        else if (tokens[0].equals("obtainLocationReport") && tokens.length == 3) {
                            int id = Integer.parseInt(tokens[1]);
                            int epoch = Integer.parseInt(tokens[2]);

                            // Create request
                            SecretKey secretKey = AESKeyGenerator.makeAESKey();
                            SecureMessage secureRequest = makeObtainLocationRequest(keyPair, serverKey, id, epoch, secretKey);

                            // Perform request
                            HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
                            SecureMessage secureResponse = restTemplate.postForObject(getServerURL() + "/obtain-location-report-ha", request, SecureMessage.class);

                            // Check response
                            if (secureResponse == null) {
                                System.out.println("Location Report not found");
                                continue;
                            }
                            SignedLocationReport signedReport = checkObtainLocationResponse(keyPair, serverKey, secureResponse, secretKey, id, epoch);
                            LocationReport report = checkLocationReport(signedReport, getClientPublicKey(signedReport.get_userId()));

                            // Print report
                            System.out.println( "User " + id + ", epoch " + epoch + ", location: " + report.get_location() + "\nReport: " + report );
                        }

                        // obtainUsersAtLocation, [x], [y], [ep]
                        // Specification: returns a list of users that were at position (x,y) at epoch "ep"
                        else if (tokens[0].equals("obtainUsersAtLocation") && tokens.length == 4) {
                            int x = Integer.parseInt(tokens[1]);
                            int y = Integer.parseInt(tokens[2]);
                            Location location = new Location(x, y);
                            int epoch = Integer.parseInt(tokens[3]);

                            // Create request
                            SecretKey secretKey = AESKeyGenerator.makeAESKey();
                            SecureMessage secureRequest = makeObtainUsersRequest(keyPair, serverKey, location, epoch, secretKey);

                            // Perform request
                            HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
                            SecureMessage secureResponse = restTemplate.postForObject(getServerURL() + "/users", request, SecureMessage.class);
                            if (secureResponse == null)
                                throw new IllegalArgumentException("Error in response");

                            // Check response
                            UsersAtLocation usersAtLocation = checkObtainUsersResponse(keyPair, serverKey, secureResponse, secretKey, location, epoch);
                            for (SignedLocationReport signedReport : usersAtLocation.get_reports())
                                checkLocationReport(signedReport, getClientPublicKey(signedReport.get_userId()));

                            // Print reports
                            System.out.println(usersAtLocation);
                        }

                        else {
                            System.out.println("Unknown command");
                        }

                    } catch (IllegalArgumentException e) {
                        System.out.println("Caught exception with description:" + e.getMessage());
                    }
                }

            } catch (NoSuchElementException e) {
                // no line was found by the scanner -> exit client
            }

            exit(0);
        };
    }

    /* ========================================================== */
    /* ====[              Auxiliary functions               ]==== */
    /* ========================================================== */

    private String getServerURL() {
        return "http://localhost:" + SERVER_PORT;
    }

    public static PublicKey getClientPublicKey(int clientId) throws GeneralSecurityException, IOException {
        String keyPath = RSAKeyGenerator.KEYS_PATH + clientId + ".pub";
        return RSAKeyGenerator.readPublicKey(keyPath);
    }

    public LocationReport checkLocationReport(SignedLocationReport signedReport, PublicKey verifyKey) throws Exception {
        // Check report
        signedReport.verify(verifyKey);
        int validProofCount = signedReport.verifyProofs();
        if (validProofCount <= BYZANTINE_USERS)
            throw new ReportNotAcceptableException("Not enough proofs to constitute an acceptable Location Report");

        // Return safe report
        return signedReport.get_report();
    }

    /* ========================================================== */
    /* ====[             Obtain Location Report             ]==== */
    /* ========================================================== */

    public SecureMessage makeObtainLocationRequest(KeyPair keyPair, PublicKey serverKey, int id, int epoch, SecretKey secretKey) throws Exception {
        ObtainLocationRequest locationRequest = new ObtainLocationRequest(id, epoch);
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes = objectMapper.writeValueAsBytes(locationRequest);
        return new SecureMessage(bytes, secretKey, serverKey, keyPair.getPrivate());
    }

    public SignedLocationReport checkObtainLocationResponse(KeyPair keyPair, PublicKey serverKey, SecureMessage secureResponse, SecretKey secretKey, int id, int epoch) throws Exception {
        // Check response's secret key for freshness
        if (!secureResponse.getSecretKey(keyPair.getPrivate()).equals( secretKey ))
            throw new IllegalArgumentException("Server response not fresh!");

        // Decipher and check response signature
        byte[] messageBytes = secureResponse.decipherAndVerify(keyPair.getPrivate(), serverKey);
        SignedLocationReport report = SignedLocationReport.getFromBytes(messageBytes);

        // Check content
        if (report.get_userId() != id || report.get_epoch() != epoch)
            throw new IllegalArgumentException("Bad server response!");

        return report;
    }


    /* ========================================================== */
    /* ====[            Obtain Users at Location            ]==== */
    /* ========================================================== */

    public SecureMessage makeObtainUsersRequest(KeyPair keyPair, PublicKey serverKey, Location location, int epoch, SecretKey secretKey) throws Exception {
        ObtainUsersRequest usersRequest = new ObtainUsersRequest(location, epoch);
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes = objectMapper.writeValueAsBytes(usersRequest);
        return new SecureMessage(bytes, secretKey, serverKey, keyPair.getPrivate());
    }

    public UsersAtLocation checkObtainUsersResponse(KeyPair keyPair, PublicKey serverKey, SecureMessage secureResponse, SecretKey secretKey, Location location, int epoch) throws Exception {
        // Check response's secret key for freshness
        if (!secureResponse.getSecretKey(keyPair.getPrivate()).equals( secretKey ))
            throw new IllegalArgumentException("Server response not fresh!");

        // Decipher and check response signature
        byte[] messageBytes = secureResponse.decipherAndVerify(keyPair.getPrivate(), serverKey);
        UsersAtLocation response = UsersAtLocation.getFromBytes(messageBytes);

        // Check content
        if (!response.get_location().equals(location) || response.get_epoch() != epoch)
            throw new IllegalArgumentException("Bad server response!");

        return response;
    }

}