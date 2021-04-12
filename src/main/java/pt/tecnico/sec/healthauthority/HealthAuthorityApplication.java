package pt.tecnico.sec.healthauthority;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

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

            obtainUsersAtLocation, [pos], [ep]
            > returns a list of users that were at position "pos" at epoch "ep"

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

                        // obtainLocationReport, [userId], [ep]
                        // Specification: returns the position of "userId" at the epoch "ep"
                        else if (tokens[0].equals("obtainLocationReport") && tokens.length == 3) {
                            int userId = Integer.parseInt(tokens[1]);
                            int ep = Integer.parseInt(tokens[2]);
                            // TODO : send request to server
                        }

                        // obtainUsersAtLocation, [pos], [ep]
                        // Specification: returns a list of users that were at position "pos" at epoch "ep"
                        else if (tokens[0].equals("obtainUsersAtLocation") && tokens.length == 3) {
                            int pos = Integer.parseInt(tokens[1]);
                            int ep = Integer.parseInt(tokens[2]);
                            // TODO : send request to server
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

}