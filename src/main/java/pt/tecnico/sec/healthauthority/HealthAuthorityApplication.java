package pt.tecnico.sec.healthauthority;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.JavaKeyStore;
import pt.tecnico.sec.client.*;
import pt.tecnico.sec.server.exception.ReportNotAcceptableException;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;

import static java.lang.System.exit;
import static pt.tecnico.sec.Constants.*;

@SpringBootApplication(exclude=DataSourceAutoConfiguration.class)
public class HealthAuthorityApplication {

    private RestTemplate _restTemplate;
    private static JavaKeyStore _keyStore;
    private static int _serverCount;

    private static final Map<Integer, Integer> _sKeysUsages = new HashMap<>();

    public static void main(String[] args) {
        try {
            _serverCount = Integer.parseInt(args[0]);

            // Instantiate KeyStore
            int haId = 0;
            String keyStoreName = "ha" + haId + KEYSTORE_EXTENSION;
            String keyStorePassword = "ha" + haId;
            _keyStore = new JavaKeyStore(KEYSTORE_TYPE, keyStorePassword, keyStoreName);
            _keyStore.loadKeyStore();

            SpringApplication app = new SpringApplication(HealthAuthorityApplication.class);
            app.setDefaultProperties(Collections.singletonMap("server.port", HA_BASE_PORT));
            app.run(args);
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(HA_USAGE);
        }
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    public CommandLineRunner run(RestTemplate restTemplate) {
        return args -> {
            _restTemplate = restTemplate;

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
                            System.out.println( getHelpString() );
                        }

                        // obtainLocationReport, [userId], [ep]
                        // Specification: returns the position of "userId" at the epoch "ep"
                        else if (tokens[0].equals("obtainLocationReport") && tokens.length == 3) {
                            int id = Integer.parseInt(tokens[1]);
                            int epoch = Integer.parseInt(tokens[2]);
                            if (epoch < 0 || id < 0) throw new IllegalArgumentException("Epoch and ID must be positive.");

                            LocationReport report = obtainReport(id, epoch);
                            if (report == null) System.out.println("Location Report not found");
                            else {
                                System.out.println("User " + id + ", epoch " + epoch + ", location: " + report.get_location() + "\nReport: " + report);
                            }
                        }

                        // obtainUsersAtLocation, [x], [y], [ep]
                        // Specification: returns a list of users that were at position (x,y) at epoch "ep"
                        else if (tokens[0].equals("obtainUsersAtLocation") && tokens.length == 4) {
                            Location location = new Location( Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]) );
                            int epoch = Integer.parseInt(tokens[3]);
                            if (epoch < 0) throw new IllegalArgumentException("Epoch must be positive.");

                            UsersAtLocation usersAtLocation = obtainUsers(location, epoch);
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

    // auxiliary function: returns a string with the help message
    @SuppressWarnings("SameReturnValue")
    private static String getHelpString() {
        return """
                ======================== Available Commands ========================
                obtainLocationReport, [userId], [ep]
                > returns the position of "userId" at the epoch "ep"
                obtainUsersAtLocation, [x], [y], [ep]
                > returns a list of users that were at position (x,y) at epoch "ep"
                exit
                > exits the Health Authority application
                ====================================================================
                """;
    }

    public String getServerURL(int serverId) {
        int serverPort = SERVER_BASE_PORT + serverId;
        return "http://localhost:" + serverPort;
    }

    public LocationReport checkLocationReport(SignedLocationReport signedReport, PublicKey verifyKey) throws Exception {
        // Check report
        signedReport.verify(verifyKey);
        try {
            signedReport.verifyProofs( _keyStore.getAllUsersPublicKeys() );
        } catch (ReportNotAcceptableException e) {
            throw new IllegalArgumentException("Bad server response!");
        }

        // Return safe report
        return signedReport.get_report();
    }

    public void secretKeyUsed(int id) {
        _sKeysUsages.put(id, _sKeysUsages.get(id)+1); // update secret key usages
    }

    public int getRandomServerId() {
        Random random = new Random();
        return random.nextInt(_serverCount);
    }


    /* ========================================================== */
    /* ====[             Obtain Location Report             ]==== */
    /* ========================================================== */

    public LocationReport obtainReport(int userId, int epoch) throws Exception {
        // Create request
        ObtainLocationRequest locationRequest = new ObtainLocationRequest(userId, epoch);
        Message request = new Message(0, locationRequest);

        // Perform request
        Message response = postToServers(request, "/obtain-location-report");

        // Check response
        if (response.get_data() == null) return null;
        response.throwIfException();
        SignedLocationReport signedReport = checkObtainLocationResponse(response, userId, epoch);
        return checkLocationReport(signedReport, _keyStore.getPublicKey("user" + signedReport.get_userId()) );
    }


    public SignedLocationReport checkObtainLocationResponse(Message response, int id, int epoch) {
        SignedLocationReport report = response.retrieveSignedLocationReport();
        // Check content
        if (report.get_userId() != id || report.get_epoch() != epoch)
            throw new IllegalArgumentException("Bad server response!");

        return report;
    }


    /* ========================================================== */
    /* ====[            Obtain Users at Location            ]==== */
    /* ========================================================== */

    public UsersAtLocation obtainUsers(Location location, int epoch) throws Exception {
        // Create request
        ObtainUsersRequest usersRequest = new ObtainUsersRequest(location, epoch);
        Message request = new Message(0, usersRequest);

        // Perform request
        Message response = postToServers(request, "/users");
        if (response == null)
            throw new IllegalArgumentException("Error in response");

        // Check response
        response.throwIfException();
        UsersAtLocation usersAtLocation = checkObtainUsersResponse(response, location, epoch);
        for (SignedLocationReport signedReport : usersAtLocation.get_reports())
            checkLocationReport(signedReport, _keyStore.getPublicKey("user" + signedReport.get_userId()) );
        return usersAtLocation;
    }

    public UsersAtLocation checkObtainUsersResponse(Message response, Location location, int epoch) {
        UsersAtLocation users = response.retrieveUsersAtLocation();
        // Check content
        if (!users.get_location().equals(location) || users.get_epoch() != epoch)
            throw new IllegalArgumentException("Bad server response!");

        return users;
    }


    /* ========================================================== */
    /* ====[              Server communication              ]==== */
    /* ========================================================== */

    private Message sendRequest(int serverId, SecureMessage secureRequest, SecretKey secretKey, String endpoint) throws Exception {
        PublicKey serverKey = _keyStore.getPublicKey("server" + serverId);
        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + endpoint, request, SecureMessage.class);

        if (secureResponse == null) return null;

        // Check response's signature and decipher TODO freshness
        return secureResponse.decipherAndVerifyMessage( secretKey, serverKey);
    }

    private Message postToServers(Message message, String endpoint) throws Exception {
        int serverId = getRandomServerId(); // Choose random server to send request
        SecretKey secretKey = updateSecretKey(serverId);
        secretKeyUsed(serverId);
        System.out.println("Requesting from server " + serverId);
        SecureMessage secureRequest = new SecureMessage(-1, message, secretKey, _keyStore.getPersonalPrivateKey());
        return sendRequest(serverId, secureRequest, secretKey, endpoint);
    }

    private Message postKeyToServers(int serverId, SecretKey keyToSend) throws Exception {
        PublicKey serverKey = _keyStore.getPublicKey("server" + serverId);
        PrivateKey myKey = _keyStore.getPersonalPrivateKey();
        SecureMessage secureRequest = new SecureMessage(-1, keyToSend, serverKey, myKey);

        HttpEntity<SecureMessage> httpRequest = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + "/secret-key", httpRequest, SecureMessage.class);

        // Check response's signature and decipher TODO freshness
        assert secureResponse != null;
        return secureResponse.decipherAndVerifyMessage( myKey, serverKey);
    }


    /* ========================================================== */
    /* ====[               Handle Secret Keys               ]==== */
    /* ========================================================== */

    public boolean secretKeyValid(int serverId) {
        Integer usages = _sKeysUsages.get(serverId);
        return usages != null && usages <= SECRET_KEY_DURATION;
    }

    public SecretKey updateSecretKey(int serverId) throws Exception {
        if (!secretKeyValid(serverId)){
            System.out.print("Generating new secret key... ");

            // Generate secret key
            SecretKey newSecretKey = AESKeyGenerator.makeAESKey();

            // Send key
            Message response = postKeyToServers(serverId, newSecretKey);

            // Check response
            response.throwIfException();
            if (!response.isOKString())
                throw new IllegalArgumentException("Error exchanging new secret key");

            // Success! Update key
            _keyStore.setAndStoreSecretKey("server" + serverId, newSecretKey);
            _sKeysUsages.put(serverId, 0);

            System.out.println("Done!");
        }
        return _keyStore.getSecretKey("server" + serverId);
    }
}