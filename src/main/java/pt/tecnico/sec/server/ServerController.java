package pt.tecnico.sec.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pt.tecnico.sec.RSAKeyGenerator;

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
    public void reportLocation(@RequestBody byte[] cipheredReport){
        try {
            // decipher report
            byte[] data = RSAKeyGenerator.decrypt(cipheredReport, _serverApp.getPrivateKey());
            ObjectMapper objectMapper = new ObjectMapper();
            LocationReport report = objectMapper.readValue(data, LocationReport.class);

            // handle received report
            _serverApp.reportLocation(report);
            int userId = report.get_userId();
            int epoch = report.get_epoch();

            // Check if already exists a report with the same userId and epoch
            if (reportRepository.findReportByEpochAndUser(userId, epoch) == null) {
                reportRepository.save(report);
            } else {
                System.out.println("Report for userId " + userId + " and epoch " + epoch + " already exists.\n");
            }

            /*
            for (LocationReport r : reportRepository.findAll() ){
                System.out.println(r.toString());
            }*/

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @GetMapping("/location-report/{epoch}/{userId}")
    public LocationReport getLocation(@PathVariable(value = "userId") int userId, @PathVariable(value = "epoch") int epoch){
        return reportRepository.findReportByEpochAndUser(userId, epoch);
    }
}
