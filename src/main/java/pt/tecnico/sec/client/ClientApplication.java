package pt.tecnico.sec.client;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@SpringBootApplication
public class ClientApplication {

    // USAGE:  ./mvnw spring-boot:run -Dspring-boot.run.arguments="8083" -Dstart-class=pt.tecnico.sec.client.ClientApplication

    public static void main(String[] args) {
        try {
            int port = Integer.parseInt(args[0]);
            if (port < 1024 || port > 65535)
                throw new NumberFormatException();
        }
        catch (Exception e) {
            System.out.println("Please choose a Port value between 1024 and 65535.");
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
                String hello = restTemplate.getForObject("http://localhost:8080/hello", String.class);
                System.out.println(hello);
        };
    }

}
