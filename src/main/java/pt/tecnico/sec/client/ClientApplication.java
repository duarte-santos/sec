package pt.tecnico.sec.client;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.EnvironmentGenerator;
import pt.tecnico.sec.RSAKeyGenerator;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

import static java.lang.System.exit;

@SpringBootApplication
public class ClientApplication {

    /* constants definition */
    private static final String USAGE = "Usage: ./mvnw spring-boot:run -Dspring-boot.run.arguments=\"[ID]\" -\"Dstart-class=pt.tecnico.sec.client.ClientApplication";
    private static final String EXCEPTION_STR = "Caught exception with description: ";
    private static final String EXIT_CMD = "exit";
    private static final String HELP_CMD = "help";
    private static final String STEP_CMD = "step";
    private static final String SUBMIT_CMD = "submit";
    private static final String OBTAIN_CMD = "obtain";
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

            // get keys
            String keysPath = RSAKeyGenerator.KEYS_PATH;
            KeyPair keyPair = RSAKeyGenerator.readKeyPair(keysPath + id + ".pub", keysPath + id + ".priv");
            PublicKey serverKey = RSAKeyGenerator.readPublicKey(keysPath + "server.pub");

            // create user
            _user = new User(_environment.getGrid(_epoch), id, keyPair, serverKey);
            System.out.println("The user \"C00lD0060 No." + id + "\" has SPAWNED.\n");

            // create spring application
            int port = BASE_PORT + id;
            SpringApplication springApplication = new SpringApplication(ClientApplication.class);
            springApplication.setDefaultProperties(Collections.singletonMap("server.port", String.valueOf(port)));
            springApplication.run(args);

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
                        System.out.print("\r  \n> Type your command ('help' to view available commands)\n> ");

                        // read next line
                        String line = scanner.nextLine();
                        String[] tokens = line.trim().split("\\s*,\\s*");

                        // exit - exit client
                        if (line.equals(EXIT_CMD))
                            break;

                            // step - increase epoch
                        else if (line.equals(STEP_CMD)) {
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

                        // submit [epoch] - user submits the DBLocation report of the given epoch to the server
                        else if (tokens[0].equals(SUBMIT_CMD) && tokens.length == 2) {
                            int ep = Integer.parseInt(tokens[1]);
                            if (ep >= _epoch) { // check proofs have been obtained
                                System.out.println("No proofs to send for this epoch yet.");
                                continue;
                            }

                            Location DBLocation = _environment.getGrid(ep).getUserLocation(_user.getId());
                            _user.reportLocation(ep, DBLocation);
                        }

                        // obtain [epoch] - user asks server for its DBLocation report at the given epoch
                        else if (tokens[0].equals(OBTAIN_CMD) && tokens.length == 2) {
                            int ep = Integer.parseInt(tokens[1]);
                            LocationReport locationReport = _user.obtainReport(ep);
                            if (locationReport == null) {
                                System.out.println("The requested report doesn't exist\n");
                            } else System.out.println("Location Report: " + locationReport);
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
    private static String getHelpString() {
        return """
                  ====================== Available Commands ======================
                  step           - Increase current epoch
                  submit [epoch] - Send the user's DBLocation report of the given
                                    epoch to the server
                  obtain [epoch] - Ask the server for the user's DBLocation report
                                    at the given epoch
                  exit           - Quit Client App
                  ================================================================
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