package pt.tecnico.sec.healthauthority;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.client.*;
import pt.tecnico.sec.server.exception.ReportNotAcceptableException;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;

import static java.lang.System.exit;

@SpringBootApplication(exclude=DataSourceAutoConfiguration.class)
public class HealthAuthorityApplication {

    // constants
    private static final int SERVER_BASE_PORT = 9000;
    private static final int BYZANTINE_USERS  = 1;
    private static final int HA_PORT          = 6000;

    private static final String USAGE = "Usage: ./mvnw spring-boot:run -Dspring-boot.run.arguments=\"[serverCount]\" -\"Dstart-class=pt.tecnico.sec.healthauthority.HealthAuthorityApplication";
    private static final String HELP          = """
            ======================== Available Commands ========================
            obtainLocationReport, [userId], [ep]
            > returns the position of "userId" at the epoch "ep"

            obtainUsersAtLocation, [x], [y], [ep]
            > returns a list of users that were at position (x,y) at epoch "ep"

            exit
            > exits the Health Authority application
            ====================================================================
            """;
    
    private static int _serverCount;

    public static void main(String[] args) {
        try {
            _serverCount = Integer.parseInt(args[0]);

            SpringApplication app = new SpringApplication(HealthAuthorityApplication.class);
            app.setDefaultProperties(Collections.singletonMap("server.port", HA_PORT));
            app.run(args);
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(USAGE);
        }
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

            PublicKey[] serverKeys = new PublicKey[_serverCount];
            for (int serverId = 0; serverId < _serverCount; serverId++){
                serverKeys[serverId] = RSAKeyGenerator.readServerPublicKey(serverId);
            }
            PublicKey serverKey = serverKeys[0]; // FIXME

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
                            ObtainLocationRequest locationRequest = new ObtainLocationRequest(id, epoch);
                            ObjectMapper objectMapper = new ObjectMapper();
                            byte[] requestBytes = objectMapper.writeValueAsBytes(locationRequest);

                            // Perform request
                            byte[] responseBytes = postToServer(restTemplate, keyPair.getPrivate(), serverKeys, requestBytes, secretKey, "/obtain-location-report-ha");

                            // Check response
                            if (responseBytes == null) {
                                System.out.println("Location Report not found");
                                continue;
                            }
                            SignedLocationReport signedReport = checkObtainLocationResponse(responseBytes, id, epoch);
                            LocationReport report = checkLocationReport(signedReport, RSAKeyGenerator.readClientPublicKey(signedReport.get_userId()));

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
                            ObtainUsersRequest usersRequest = new ObtainUsersRequest(location, epoch);
                            ObjectMapper objectMapper = new ObjectMapper();
                            byte[] requestBytes = objectMapper.writeValueAsBytes(usersRequest);

                            // Perform request
                            byte[] responseBytes = postToServer(restTemplate, keyPair.getPrivate(), serverKeys, requestBytes, secretKey, "/users");
                            if (responseBytes == null)
                                throw new IllegalArgumentException("Error in response");

                            // Check response
                            UsersAtLocation usersAtLocation = checkObtainUsersResponse(responseBytes, location, epoch);
                            for (SignedLocationReport signedReport : usersAtLocation.get_reports())
                                checkLocationReport(signedReport, RSAKeyGenerator.readClientPublicKey(signedReport.get_userId()));

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

    public String getServerURL(int serverId) {
        int serverPort = SERVER_BASE_PORT + serverId;
        return "http://localhost:" + serverPort;
    }

    private String[] getServerURLs() {
        String[] servers = new String[_serverCount];
        int serverPort;
        for (int serverId = 0; serverId < _serverCount; serverId++) {
            serverPort = SERVER_BASE_PORT + serverId;
            servers[serverId] = "http://localhost:" + serverPort;
        }
        return servers;
    }

    private byte[] postToServer(RestTemplate restTemplate, PrivateKey privateKey, PublicKey[] serverKeys, byte[] messageBytes, SecretKey secretKey, String endpoint) throws Exception {
        List<byte[]> responsesBytes = new ArrayList<>();
        for (int serverId = 0; serverId < serverKeys.length; serverId++) {
            PublicKey serverKey = serverKeys[serverId];
            SecureMessage secureRequest = new SecureMessage(messageBytes, secretKey, serverKey, privateKey);
            HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
            SecureMessage secureResponse = restTemplate.postForObject(getServerURL(serverId) + endpoint, request, SecureMessage.class);

            if (secureResponse == null) {
                responsesBytes.add(null);
                continue;
            }

            // Check response's freshness, signature and decipher
            if (!secureResponse.getSecretKey(privateKey).equals( secretKey ))
                throw new IllegalArgumentException("Server response not fresh!");
            responsesBytes.add( secureResponse.decipherAndVerify(privateKey, serverKey) );
        }

        return responsesBytes.get(0); //FIXME
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

    public SignedLocationReport checkObtainLocationResponse(byte[] messageBytes, int id, int epoch) throws Exception {
        SignedLocationReport report = SignedLocationReport.getFromBytes(messageBytes);

        // Check content
        if (report.get_userId() != id || report.get_epoch() != epoch)
            throw new IllegalArgumentException("Bad server response!");

        return report;
    }


    /* ========================================================== */
    /* ====[            Obtain Users at Location            ]==== */
    /* ========================================================== */

    public UsersAtLocation checkObtainUsersResponse(byte[] messageBytes, Location location, int epoch) throws Exception {
        UsersAtLocation response = UsersAtLocation.getFromBytes(messageBytes);

        // Check content
        if (!response.get_location().equals(location) || response.get_epoch() != epoch)
            throw new IllegalArgumentException("Bad server response!");

        return response;
    }

}