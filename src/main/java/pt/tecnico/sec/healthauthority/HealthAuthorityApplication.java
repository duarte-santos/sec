package pt.tecnico.sec.healthauthority;

import org.json.simple.parser.ParseException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.client.LocationReport;
import pt.tecnico.sec.client.ObtainLocationRequest;
import pt.tecnico.sec.client.SecureLocationReport;
import pt.tecnico.sec.client.SecureObtainLocationRequest;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;

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

    public static void main(String[] args) throws IOException, ParseException {
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
                        System.out.print("\r  \n> Type your command ('help' to view available commands)\n> ");

                        // read command line and parse arguments
                        String line = scanner.nextLine();
                        String[] tokens = line.trim().split("\\s*,\\s*");

                        if (line.equals("exit")) {
                            break;
                        }

                        else if (line.equals("help")) {
                            System.out.println(HELP);
                        }

                        // TODO : sign requests
                        // TODO : server verify HA signature

                        // obtainLocationReport, [userId], [ep]
                        // Specification: returns the position of "userId" at the epoch "ep"
                        else if (tokens[0].equals("obtainLocationReport") && tokens.length == 3) {
                            int userId = Integer.parseInt(tokens[1]);
                            int ep = Integer.parseInt(tokens[2]);

                            ObtainLocationRequest locationRequest = new ObtainLocationRequest(userId, ep);
                            SecureObtainLocationRequest secureLocationRequest = new SecureObtainLocationRequest(locationRequest, keyPair.getPrivate());
                            HttpEntity<SecureObtainLocationRequest> request = new HttpEntity<>(secureLocationRequest);
                            SecureLocationReport secureLocationReport = restTemplate.postForObject(getServerURL() + "/obtain-location-report-ha", request, SecureLocationReport.class);

                            if (secureLocationReport == null) {
                                System.out.println("Location Report not found");
                                continue;
                            }

                            // Decipher and check signature
                            LocationReport locationReport = secureLocationReport.decipherAndVerify(keyPair.getPrivate(), serverKey);
                            System.out.println(locationReport.get_location());
                        }

                        // obtainUsersAtLocation, [x], [y], [ep]
                        // Specification: returns a list of users that were at position (x,y) at epoch "ep"
                        else if (tokens[0].equals("obtainUsersAtLocation") && tokens.length == 4) {
                            int x = Integer.parseInt(tokens[1]);
                            int y = Integer.parseInt(tokens[2]);
                            int ep = Integer.parseInt(tokens[3]);

                            Map<String, Integer> params = new HashMap<>();
                            params.put("epoch", ep);
                            params.put("x", x);
                            params.put("y", y);

                            Integer userId = restTemplate.getForObject(getServerURL() + "/users/{epoch}/{x}/{y}", Integer.class, params);

                            if (userId == null) {
                                System.out.println("User not found");
                            }
                            else {
                                System.out.println("UserId: " + userId);
                            }
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

    public String getServerURL() {
        return "http://localhost:" + SERVER_PORT;
    }

}