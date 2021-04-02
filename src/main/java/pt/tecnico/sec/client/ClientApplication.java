package pt.tecnico.sec.client;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Scanner;

@SpringBootApplication
public class ClientApplication {

    /* constants definition */
    private static final String USAGE = "Usage: ./mvnw spring-boot:run -Dspring-boot.run.arguments=\"[PORT]\" -Dstart-class=pt.tecnico.sec.client.ClientApplication";
    private static final String EXCEPTION_STR = "Caught exception with description: ";
    private static final String EXIT_CMD = "exit";
    private static final String HELP_CMD = "help";


    public static void main(String[] args) {
        try {
            int port = Integer.parseInt(args[0]);
            if (port < 1024 || port > 65535)
                throw new NumberFormatException();
        }
        catch (NumberFormatException e) {
            System.out.println("Please choose a port value between 1024 and 65535.");
            System.out.println(USAGE);
            return;
        }
        SpringApplication springApplication = new SpringApplication(ClientApplication.class);
        springApplication.setDefaultProperties(Collections.singletonMap("server.port", args[0]));
        springApplication.run(args);
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    public CommandLineRunner run(RestTemplate restTemplate) throws Exception {
        return args -> {
                //String hello = restTemplate.getForObject("http://localhost:8080/hello", String.class);
                //System.out.println(hello);

                User user = new User(restTemplate);
                System.out.println("The user has SPAWNED.\n");

                try (Scanner scanner = new Scanner(System.in)) {
                    while (true) { //TODO only allow 1 per epoch

                        System.out.print("> Type your command ('help' to view available commands)\n> ");

                        // read next line
                        String line = scanner.nextLine();
                        String[] tokens = line.trim().split("\\s*,\\s*");

                        // exit - exit client
                        if (line.equals(EXIT_CMD))
                            break;

                        // step - increase epoch
                        else if (line.equals("step")) {
                            user.getGrid().step();
                        }

                        // location [x] [y] - change user's location
                        else if (tokens[0].equals("location") && tokens.length == 3) {
                            try {
                                int x = Integer.parseInt(tokens[1]);
                                int y = Integer.parseInt(tokens[2]);
                                user.setLocation(x, y);
                            } catch (NumberFormatException e) {
                                System.out.println("'Location' command exception: x and y must be integers and must be inside the grid range.");
                            }
                        }

                        // submit [epoch] - user submits the location report of the given epoch to the server
                        else if (tokens[0].equals("submit") && tokens.length == 2) {
                            try {
                                int ep = Integer.parseInt(tokens[1]);
                                user.reportLocation(ep);
                            } catch (IllegalArgumentException e) {
                                System.out.println("'submit' command exception: ep must be an integer and must not exceed the current epoch.");
                            }
                        }

                        // obtain [epoch] - user asks server for its location report at the given epoch
                        else if (tokens[0].equals("obtain") && tokens.length == 2) {
                            try {
                                int ep = Integer.parseInt(tokens[1]);
                                user.obtainReport(ep);
                            } catch (NumberFormatException e) {
                                System.out.println("'obtain' command exception: ep must be an integer and must not exceed the current epoch.");
                            }
                        }

                        // help
                        else if (line.equals(HELP_CMD)) {
                            // TODO show commands
                            break;
                        }

                        // other
                        else
                            System.out.println("Unknown command");

                    }
                } catch (NoSuchElementException e) {
                    // no line was found by the scanner - exit client
                }
            };
        }

}
