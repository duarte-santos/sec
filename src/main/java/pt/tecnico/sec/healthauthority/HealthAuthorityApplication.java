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
import pt.tecnico.sec.client.Location;
import pt.tecnico.sec.client.LocationReport;
import pt.tecnico.sec.client.ObtainLocationRequest;
import pt.tecnico.sec.client.SecureMessage;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
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
                        System.out.print("\n> Type your command ('help' to view available commands)\n> ");

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

                            // create secure request
                            ObtainLocationRequest locationRequest = new ObtainLocationRequest(id, epoch);
                            ObjectMapper objectMapper = new ObjectMapper();
                            byte[] bytes = objectMapper.writeValueAsBytes(locationRequest);
                            SecretKey secretKey = AESKeyGenerator.makeAESKey();
                            SecureMessage secureRequest = new SecureMessage(bytes, secretKey, serverKey, keyPair.getPrivate());

                            SecureMessage secureMessage;
                            try {
                                // send secure request
                                HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
                                secureMessage = restTemplate.postForObject(getServerURL() + "/obtain-location-report-ha", request, SecureMessage.class);
                            }
                            catch (Exception e) {
                                System.out.println(e.getMessage());
                                continue;
                            }

                            if (secureMessage == null) {
                                System.out.println("Location Report not found");
                                continue;
                            }

                            // check secret key for freshness
                            if (!secureMessage.getSecretKey(keyPair.getPrivate()).equals( secretKey ))
                                throw new IllegalArgumentException("Server response not fresh!");

                            // decipher and check signature
                            byte[] messageBytes = secureMessage.decipherAndVerify(keyPair.getPrivate(), serverKey);
                            LocationReport locationReport = LocationReport.getFromBytes(messageBytes);

                            // check content
                            if (locationReport.get_userId() != id || locationReport.get_epoch() != epoch)
                                throw new IllegalArgumentException("Bad server response!");

                            System.out.println(locationReport.get_location());
                        }

                        // obtainUsersAtLocation, [x], [y], [ep]
                        // Specification: returns a list of users that were at position (x,y) at epoch "ep"
                        else if (tokens[0].equals("obtainUsersAtLocation") && tokens.length == 4) {
                            int x = Integer.parseInt(tokens[1]);
                            int y = Integer.parseInt(tokens[2]);
                            int epoch = Integer.parseInt(tokens[3]);

                            // create secure request
                            Location location = new Location(x, y);
                            ObtainUsersRequest usersRequest = new ObtainUsersRequest(location, epoch);
                            ObjectMapper objectMapper = new ObjectMapper();
                            byte[] bytes = objectMapper.writeValueAsBytes(usersRequest);
                            SecretKey secretKey = AESKeyGenerator.makeAESKey();
                            SecureMessage secureRequest = new SecureMessage(bytes, secretKey, serverKey, keyPair.getPrivate());

                            SecureMessage secureMessage;
                            try {
                                // send secure request
                                HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
                                secureMessage = restTemplate.postForObject(getServerURL() + "/users", request, SecureMessage.class);
                                if (secureMessage == null)
                                    throw new IllegalArgumentException("Error in response");
                            }
                            catch (Exception e) {
                                System.out.println(e.getMessage());
                                continue;
                            }

                            // check secret key for freshness
                            if (!secureMessage.getSecretKey(keyPair.getPrivate()).equals( secretKey ))
                                throw new IllegalArgumentException("Server response not fresh!");

                            // decipher and check signature
                            byte[] messageBytes = secureMessage.decipherAndVerify(keyPair.getPrivate(), serverKey);
                            UsersAtLocation usersAtLocation = UsersAtLocation.getFromBytes(messageBytes);
                            List<Integer> userIds = usersAtLocation.get_userIds();

                            // check content
                            if (!usersAtLocation.get_location().equals(location) || usersAtLocation.get_epoch() != epoch)
                                throw new IllegalArgumentException("Bad server response!");

                            // print response
                            if (userIds.size() == 0) {
                                System.out.println("No users at that location in that epoch.");
                                continue;
                            }
                            System.out.print("UserIds: ");
                            for (Integer userId : userIds) {
                                System.out.print(userId + " ");
                            }
                            System.out.println();
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

    private String getServerURL() {
        return "http://localhost:" + SERVER_PORT;
    }

}