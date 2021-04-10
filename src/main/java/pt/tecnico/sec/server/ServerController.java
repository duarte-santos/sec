package pt.tecnico.sec.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pt.tecnico.sec.client.LocationReport;
import pt.tecnico.sec.client.SecureLocationReport;
import pt.tecnico.sec.client.SignedLocationReport;


@RestController
public class ServerController {

    private final ServerApplication _serverApp;

    @Autowired
    private ServerController(ServerApplication serverApp) {
        _serverApp = serverApp;
    }

    @Autowired
    private ReportRepository reportRepository;

    @GetMapping("/hello")
    public String sayHello(@RequestParam(value = "myName", defaultValue = "World") String name) {
        return String.format("Hello %s!", name);
    }

    @PostMapping("/location-report")
    public void reportLocation(@RequestBody SecureLocationReport secureLocationReport) {
        try {
            // Decipher and check signatures
            SignedLocationReport signedReport = _serverApp.decipherReport(secureLocationReport);
            _serverApp.verifyReportSignatures(signedReport); // throws exception

            // Check if already exists a report with the same userId and epoch
            int userId = signedReport.get_userId();
            int epoch = signedReport.get_epoch();
            if (reportRepository.findReportByEpochAndUser(userId, epoch) != null) // TODO warn client
                throw new IllegalArgumentException("Report for userId " + userId + " and epoch " + epoch + " already exists.\n");

            // Save report in database
            System.out.println(signedReport);
            DBLocationReport report = new DBLocationReport(signedReport);
            reportRepository.save(report);

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @GetMapping("/location-report/{epoch}/{userId}")
    public LocationReport getLocation(@PathVariable(value = "userId") int userId, @PathVariable(value = "epoch") int epoch){
        DBLocationReport dbLocationReport = reportRepository.findReportByEpochAndUser(userId, epoch);
        return new LocationReport(dbLocationReport);
    }
}
