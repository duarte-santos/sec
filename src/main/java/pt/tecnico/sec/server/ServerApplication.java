package pt.tecnico.sec.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import pt.tecnico.sec.client.ClientApplication;

import java.util.Collections;

@SpringBootApplication
public class ServerApplication {

    private static final int BASE_PORT = 9000;

    public static void main(String[] args) {
        int port = BASE_PORT;
        SpringApplication springApplication = new SpringApplication(ServerApplication.class);
        springApplication.setDefaultProperties(Collections.singletonMap("server.port", String.valueOf(port)));
        springApplication.run(args);
    }

    public void reportLocation(LocationReport report){
        System.out.println(report.toString());
    }

}
