package pt.tecnico.sec.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pt.tecnico.sec.client.LocationReport;
import pt.tecnico.sec.client.SecureLocationReport;


@RestController
public class ServerController {

    private final ServerApplication _serverApp;

    @Autowired
    private ServerController(ServerApplication serverApp, ReportRepository reportRepository) {
        _serverApp = serverApp;
        _reportRepository = reportRepository;
    }

    private final ReportRepository _reportRepository;

    @GetMapping("/hello")
    public String sayHello(@RequestParam(value = "myName", defaultValue = "World") String name) {
        return String.format("Hello %s!", name);
    }

    @PostMapping("/location-report")
    public void reportLocation(@RequestBody SecureLocationReport secureLocationReport) {
        try {
            // Decipher and check signatures
            LocationReport locationReport = _serverApp.decipherReport(secureLocationReport);
            _serverApp.verifyReportSignatures(locationReport); // throws exception

            // Check if already exists a report with the same userId and epoch
            int userId = locationReport.get_userId();
            int epoch = locationReport.get_epoch();
            if (_reportRepository.findReportByEpochAndUser(userId, epoch) != null) // TODO warn client
                throw new IllegalArgumentException("Report for userId " + userId + " and epoch " + epoch + " already exists.\n");

            // Save report in database
            System.out.println(locationReport);
            DBLocationReport report = new DBLocationReport(locationReport);
            _reportRepository.save(report);

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @GetMapping("/location-report/{epoch}/{userId}")
    public SecureLocationReport getLocation(@PathVariable(value = "userId") int userId, @PathVariable(value = "epoch") int epoch){
        try {
            DBLocationReport dbLocationReport = _reportRepository.findReportByEpochAndUser(userId, epoch);
            if (dbLocationReport == null)
                return null; // FIXME exception
            LocationReport locationReport = new LocationReport(dbLocationReport);
            return _serverApp.secureLocationReport(locationReport);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    @GetMapping("/users/{epoch}/{x}/{y}")
    public Integer getUsers(@PathVariable(value = "epoch") int epoch, @PathVariable(value = "x") int x, @PathVariable(value = "y") int y){
        DBLocationReport dbLocationReport = _reportRepository.findUsersByLocationAndEpoch(epoch, x, y);
        if (dbLocationReport == null)
            return null;
        // FIXME : allow return multiple users
        return dbLocationReport.get_userId();
    }

}
