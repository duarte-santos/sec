package pt.tecnico.sec.healthauthority;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@SpringBootApplication
public class HealthAuthorityApplication {

    public static void main(String[] args) {
        //SpringApplication.run(HealthAuthorityApplication.class, args);
        SpringApplication app = new SpringApplication(HealthAuthorityApplication.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", "8084"));
        app.run(args);
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    public CommandLineRunner run(RestTemplate restTemplate) {
        return args -> {
                String hello = restTemplate.getForObject("http://localhost:8080/hello", String.class);
                System.out.println(hello);
        };
    }

}
