package pt.tecnico.sec.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pt.tecnico.sec.client.LocationProof;

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
        int userId = report.get_userId();
        int epoch = report.get_epoch();

        // Check if already exists a report with the same userId and epoch
        if (reportRepository.findReportByEpochAndUser(userId, epoch) == null){
            reportRepository.save(report);
        } else {
            System.out.println("Report for userId " + userId + " and epoch " + epoch + " already exists.\n");
        }

        /*
        for (LocationReport r : reportRepository.findAll() ){
            System.out.println(r.toString());
        }*/
    }

    @GetMapping("/location-report/{epoch}/{userId}")
    public LocationReport getLocation(@PathVariable(value = "userId") int userId, @PathVariable(value = "epoch") int epoch){
        return reportRepository.findReportByEpochAndUser(userId, epoch);
    }
}
