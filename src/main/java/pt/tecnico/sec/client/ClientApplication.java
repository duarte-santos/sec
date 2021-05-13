package pt.tecnico.sec.client;

import org.json.simple.parser.ParseException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.EnvironmentGenerator;
import pt.tecnico.sec.RSAKeyGenerator;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;

import static java.lang.System.exit;

@SpringBootApplication(exclude=DataSourceAutoConfiguration.class)
public class ClientApplication {

    /* constants definition */
    private static final String USAGE = "Usage: ./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.client.ClientApplication -Dspring-boot.run.arguments=\"[userId serverCount]\"";
    private static final String EXCEPTION_STR = "Caught exception with description: ";
    private static final String EXIT_CMD = "exit";
    private static final String HELP_CMD = "help";
    private static final String STEP_CMD = "step";
    private static final String SUBMIT_CMD = "submit";
    private static final String OBTAIN_CMD = "obtain";
    private static final String PROOFS_CMD = "proofs";
    private static final int BASE_PORT = 8000;

    private static Environment _environment;
    private static int _epoch;
    private static User _user;

    public static void main(String[] args) {
        try {

            // create environment
            _environment = EnvironmentGenerator.parseEnvironmentJSON(); // import from randomly generated JSON
            List<Integer> userIds = _environment.getUserList();
            System.out.println("Valid IDs: " + userIds);
            _epoch = 0;

            // get user's id
            int id = Integer.parseInt(args[0]);
            if (!userIds.contains(id))  // client must exist in environment
                throw new NumberFormatException("Invalid user ID. Please choose an ID value from the ones shown.");

            // get serverIds from the serverCount
            int serverCount = Integer.parseInt(args[1]);
            if (serverCount <= 0)  // client must exist in environment
                throw new NumberFormatException("Argument 'serverCount' be a positive integer.");

            // get keys
            String keysPath = RSAKeyGenerator.KEYS_PATH + "c" + id;
            KeyPair keyPair = RSAKeyGenerator.readKeyPair(keysPath + ".pub", keysPath + ".priv");
            // get all server keys
            PublicKey[] serverKeys = new PublicKey[serverCount];
            for (int serverId = 0; serverId < serverCount; serverId++) {
                PublicKey serverKey = RSAKeyGenerator.readServerPublicKey(serverId);
                serverKeys[serverId] = serverKey;
            }

            // create user
            _user = new User(_environment.getGrid(_epoch), id, keyPair, serverKeys);
            System.out.println("The user \"C00lD0060 No." + id + "\" has SPAWNED.\n");

            // create spring application
            int port = BASE_PORT + id;
            SpringApplication springApplication = new SpringApplication(ClientApplication.class);
            springApplication.setDefaultProperties(Collections.singletonMap("server.port", String.valueOf(port)));
            springApplication.run(args);

        } catch (IOException|ParseException|GeneralSecurityException e) {
            System.out.println("Error setting up client. Please make sure to properly run EnvironmentGenerator and RSAKeyGenerator before running the client.");
        } catch (Exception e) {
            System.out.println(EXCEPTION_STR + e.getMessage());
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
            _user.setRestTemplate(restTemplate);

            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    try {
                        System.out.print("\r  \n\n> Type your command ('help' to view available commands)\n> ");

                        // read next line
                        String line = scanner.nextLine();
                        String[] tokens = line.trim().split("\\s*,\\s*");

                        // exit - exit client
                        if (line.equals(EXIT_CMD))
                            break;

                        // step - increase epoch
                        else if (line.equals(STEP_CMD)) {
                            try {
                                // check if epoch is covered by environment
                                if (_epoch > _environment.getMaxEpoch()) {
                                    System.out.println("No more steps available in environment.");
                                    continue;
                                }

                                // perform the step on _user
                                step();

                                // signal other users to step
                                for (int userId : _environment.getUserList()) {
                                    if (userId == _user.getId()) continue; // do not send request to myself
                                    _user.stepRequest(userId);
                                }
                            }
                            catch (Exception e) {
                                System.out.println(e.getLocalizedMessage());
                            }
                        }

                        // submit, [epoch] - user submits the DBLocation report of the given epoch to the server
                        else if (tokens[0].equals(SUBMIT_CMD) && tokens.length == 2) {
                            int ep = Integer.parseInt(tokens[1]);
                            if (ep >= _epoch) { // check proofs have been obtained
                                System.out.println("No proofs to send for this epoch yet.");
                                continue;
                            }

                            Location DBLocation = _environment.getGrid(ep).getUserLocation(_user.getId());
                            try {
                                String response = _user.reportLocation(ep, DBLocation);
                                System.out.println(response);
                            }
                            catch (Exception e) {
                                System.out.println(e.getMessage());
                            }
                        }

                        // obtain, [epoch] - user asks server for its DBLocation report at the given epoch
                        else if (tokens[0].equals(OBTAIN_CMD) && tokens.length == 2) {
                            int ep = Integer.parseInt(tokens[1]);
                            try {
                                LocationReport report = _user.obtainReport(ep);
                                if (report == null)
                                    System.out.println("The requested report doesn't exist");
                                else
                                    System.out.println( "User " + report.get_userId() + ", epoch " + ep + ", location: " + report.get_location() + "\nReport: " + report );
                            }
                            catch (Exception e) {
                                System.out.println(e.getMessage());
                            }
                        }

                        // proofs, [epoch1], [epoch2], (...) - user asks server for its proofs' as witness on given epochs
                        else if (tokens[0].equals(PROOFS_CMD) && tokens.length >= 2) {
                            Set<Integer> epochs = new HashSet<>();
                            for (String token : Arrays.copyOfRange(tokens, 1, tokens.length) )
                                epochs.add( Integer.parseInt(token) );

                            try {
                                List<LocationProof> proofs = _user.requestMyProofs(epochs);
                                if (proofs.size() == 0)
                                    System.out.println("No proofs generated as witness at given epochs");
                                else
                                    System.out.println(proofs);
                            }
                            catch (Exception e) {
                                System.out.println(e.getMessage());
                            }
                        }

                        // help
                        else if (line.equals(HELP_CMD)) {
                            System.out.println(getHelpString());
                        }

                        // other
                        else
                            System.out.println("Unknown command");

                    } catch (IllegalArgumentException e) {
                        System.out.println(EXCEPTION_STR + e.getMessage());
                    }
                }
            } catch (NoSuchElementException e) {
                // no line was found by the scanner - exit client
            }

            exit(0);
        };
    }

    // auxiliary function: returns a string with the help message
    @SuppressWarnings("SameReturnValue")
    private static String getHelpString() {
        return """
                  ============================= Available Commands =============================
                  step                       - Increase current epoch
                  submit, [epoch]            - Send the user's DBLocation report of the given
                                                epoch to the server
                  obtain, [epoch]            - Ask the server for the user's DBLocation report
                                                at the given epoch
                  proofs, [ep1], [ep2], ...  - Ask the server for the proofs that the user
                                                generated as witness
                  exit                       - Quit Client App
                  ==============================================================================

                """;
    }

    public static User getUser() {
        return _user;
    }

    // can be called by controller - step to synchronize
    public void step() {
        Grid nextGrid;
        int maxEpoch = _environment.getMaxEpoch();
        if (++_epoch < maxEpoch) nextGrid = _environment.getGrid(_epoch);
        else nextGrid = _environment.getGrid(maxEpoch); // after last epoch grid will remain as it was
        _user.step(nextGrid);
    }

}