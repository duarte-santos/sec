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
import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;

import static java.lang.System.exit;

@SpringBootApplication(exclude=DataSourceAutoConfiguration.class)
public class HealthAuthorityApplication {

    // constants
    private static final int SERVER_BASE_PORT = 9000;
    private static final int SECRET_KEY_DURATION = 2;
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
    private SecretKey _secretKey;
    private int _sKeyUsages;

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
                            ObtainLocationRequest locationRequest = new ObtainLocationRequest(id, epoch);
                            ObjectMapper objectMapper = new ObjectMapper();
                            byte[] requestBytes = objectMapper.writeValueAsBytes(locationRequest);

                            // Perform request
                            byte[] responseBytes = postToServers(restTemplate, keyPair.getPrivate(), serverKeys, requestBytes, "/obtain-location-report");

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
                            ObtainUsersRequest usersRequest = new ObtainUsersRequest(location, epoch);
                            ObjectMapper objectMapper = new ObjectMapper();
                            byte[] requestBytes = objectMapper.writeValueAsBytes(usersRequest);

                            // Perform request
                            byte[] responseBytes = postToServers(restTemplate, keyPair.getPrivate(), serverKeys, requestBytes, "/users");
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

    public String getStringFromBytes(byte[] bytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, String.class);
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
    /* ====[                 Get Secret Key                 ]==== */
    /* ========================================================== */

    public boolean secretKeyValid() {
        return _secretKey != null && _sKeyUsages <= SECRET_KEY_DURATION;
    }

    private List<byte[]> sendSecretKey(RestTemplate restTemplate, PrivateKey privateKey, PublicKey[] serverKeys, SecretKey keyToSend) throws Exception {
        List<byte[]> responsesBytes = new ArrayList<>();
        for (int serverId = 0; serverId < serverKeys.length; serverId++) {
            PublicKey serverKey = serverKeys[serverId];
            SecureMessage secureRequest = new SecureMessage(-1, keyToSend, serverKey, privateKey);
            responsesBytes.add( postToServer(restTemplate, serverId, serverKeys[serverId], secureRequest, keyToSend, "/secret-key") );
        }
        return responsesBytes; //FIXME
    }

    public SecretKey getSecretKey(RestTemplate restTemplate, PrivateKey privateKey, PublicKey[] serverKeys) throws Exception {
        if (!secretKeyValid()){
            System.out.print("Generating new secret key... ");

            // Generate secret key
            SecretKey newSecretKey = AESKeyGenerator.makeAESKey();

            // Send key
            List<byte[]> responsesBytes = sendSecretKey(restTemplate, privateKey, serverKeys, newSecretKey);

            // Check response
            for (byte[] responseBytes : responsesBytes) {
                if (responseBytes == null || !getStringFromBytes(responseBytes).equals("OK"))
                    throw new IllegalArgumentException("Error exchanging new secret key");
            }

            // Success! Update key
            _secretKey = newSecretKey;
            _sKeyUsages = 0;

            System.out.println("Done!");
        }
        return _secretKey;
    }

    /* ========================================================== */
    /* ====[              Server communication              ]==== */
    /* ========================================================== */

    private byte[] postToServer(RestTemplate restTemplate, int serverId, PublicKey serverKey, SecureMessage secureRequest, SecretKey secretKey, String endpoint) throws Exception {
        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = restTemplate.postForObject(getServerURL(serverId) + endpoint, request, SecureMessage.class);

        if (secureResponse == null) return null;

        // Check response's signature and decipher TODO freshness
        return secureResponse.decipherAndVerify( secretKey, serverKey);
    }

    private byte[] postToServers(RestTemplate restTemplate, PrivateKey privateKey, PublicKey[] serverKeys, byte[] messageBytes, String endpoint) throws Exception {
        SecretKey secretKey = getSecretKey(restTemplate, privateKey, serverKeys);
        SecureMessage secureRequest = new SecureMessage(-1, messageBytes, secretKey, privateKey);

        List<byte[]> responsesBytes = new ArrayList<>();
        for (int serverId = 0; serverId < serverKeys.length; serverId++) {
            responsesBytes.add( postToServer(restTemplate, serverId, serverKeys[serverId], secureRequest, secretKey, endpoint) );
        }

        _sKeyUsages++;
        return responsesBytes.get(0); //FIXME
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