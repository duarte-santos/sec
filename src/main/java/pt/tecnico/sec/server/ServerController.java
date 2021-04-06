package pt.tecnico.sec.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class ServerController {

    private final ServerApplication _serverApp;

    @Autowired
    private ServerController(ServerApplication clientApp) {
        _serverApp = clientApp;
    }

    @Autowired
    private ReportRepository reportRepository;

    @GetMapping("/hello")
    public String sayHello(@RequestParam(value = "myName", defaultValue = "World") String name) {
        return String.format("Hello %s!", name);
    }

    @PostMapping("/location-report")
    public void reportLocation(@RequestBody LocationReport report){

        _serverApp.reportLocation(report);
        // FIXME Check stuff

        /*reportRepository.save(report);
        for (LocationReport r : reportRepository.findAll() ){
            System.out.println(r.toString());
        }*/
    }
}
