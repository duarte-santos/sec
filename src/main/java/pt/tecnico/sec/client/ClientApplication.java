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

import static java.lang.System.exit;

@SpringBootApplication
public class ClientApplication {

    /* constants definition */
    private static final String USAGE = "Usage: ./mvnw spring-boot:run -Dspring-boot.run.arguments=\"[ID]\" -\"Dstart-class=pt.tecnico.sec.client.ClientApplication";
    private static final String EXCEPTION_STR = "Caught exception with description: ";
    private static final String EXIT_CMD = "exit";
    private static final String HELP_CMD = "help";
    private static final int BASE_PORT = 8000;
    private static final int SERVER_PORT = 9000;

    private static int _id;
    private static User _user;

    public static void main(String[] args) {
        try {
            _id = Integer.parseInt(args[0]);
            if (_id < 0 || _id >= 1000) // port = 8000 + id between accepted range (8000-8999)
                throw new NumberFormatException();
            int port = BASE_PORT + _id;
            SpringApplication springApplication = new SpringApplication(ClientApplication.class);
            springApplication.setDefaultProperties(Collections.singletonMap("server.port", String.valueOf(port)));
            springApplication.run(args);
        }
        catch (NumberFormatException e) {
            System.out.println("Please choose an id value between 0 and 1000.");
            System.out.println(USAGE);
        }
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    public CommandLineRunner run(RestTemplate restTemplate) throws Exception {
        return args -> {
            Environment environment = new Environment(); // import from JSON

            Grid grid0 = new Grid();
            Grid grid1 = new Grid();
            grid0.addUserLocation(0, new Location(0,0));
            grid0.addUserLocation(1, new Location(1,0));
            grid1.addUserLocation(0, new Location(0,0));
            grid1.addUserLocation(1, new Location(1,1));
            environment.addEpochGrid(0, grid0);
            environment.addEpochGrid(1, grid1);

            _user = new User(restTemplate, environment, _id);
            System.out.println("The user \"C00lD0060 No." + _id + "\" has SPAWNED.\n");

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
                        _user.step();
                    }

                    // submit [epoch] - user submits the location report of the given epoch to the server
                    else if (tokens[0].equals("submit") && tokens.length == 2) {
                        try {
                            int ep = Integer.parseInt(tokens[1]);
                            _user.reportLocation(ep);
                        } catch (IllegalArgumentException e) {
                            System.out.println("'submit' command exception: ep must be an integer and must not exceed the current epoch.");
                        }
                    }

                    // obtain [epoch] - user asks server for its location report at the given epoch
                    else if (tokens[0].equals("obtain") && tokens.length == 2) {
                        try {
                            int ep = Integer.parseInt(tokens[1]);
                            _user.obtainReport(ep);
                        } catch (NumberFormatException e) {
                            System.out.println("'obtain' command exception: ep must be an integer and must not exceed the current epoch.");
                        }
                    }

                    // help
                    else if (line.equals(HELP_CMD)) {
                        System.out.println(getHelpString());
                    }

                    // other
                    else
                        System.out.println("Unknown command");

                }

            } catch (NoSuchElementException e) {
                // no line was found by the scanner - exit client
            }

            exit(0);
        };
    }

    /* auxiliary function: returns a string with the help message */
    private static String getHelpString() {
        return 	"  ====================== Available Commands ======================\n" +
                "  submit [epoch] - Send the user's location report of the given\n" +
                "                    epoch to the server\n" +
                "  obtain [epoch] - Ask the server for the user's location report\n" +
                "                    at the given epoch\n" +
                "  step           - Increase current epoch\n" +
                "  exit           - Quit Client App\n" +
                "  ================================================================\n";
    }

    public static User getUser() {
        return _user;
    }
}
