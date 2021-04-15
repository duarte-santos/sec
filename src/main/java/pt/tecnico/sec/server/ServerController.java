package pt.tecnico.sec.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pt.tecnico.sec.client.LocationReport;
import pt.tecnico.sec.client.SecureLocationReport;
import pt.tecnico.sec.client.SecureObtainLocationRequest;
import pt.tecnico.sec.healthauthority.SecureObtainUsersRequest;
import pt.tecnico.sec.healthauthority.SecureUsersList;

import java.security.PublicKey;
import java.util.List;


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
            LocationReport locationReport = _serverApp.decipherAndVerifyReport(secureLocationReport);

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

    // used by clients
    @PostMapping("/obtain-location-report")
    public SecureLocationReport getLocation(@RequestBody SecureObtainLocationRequest secureRequest){
        try {
            int userId = secureRequest.get_request().get_userId();
            int epoch = secureRequest.get_request().get_epoch();
            secureRequest.verify( _serverApp.getClientPublicKey(userId) );

            DBLocationReport dbLocationReport = _reportRepository.findReportByEpochAndUser(userId, epoch);
            if (dbLocationReport == null)
                return null; // FIXME exception
            LocationReport report = new LocationReport(dbLocationReport);

            // encrypt using client public key, sign using server private key
            PublicKey clientKey = _serverApp.getClientPublicKey(report.get_userId());
            return new SecureLocationReport(report, clientKey, _serverApp.getPrivateKey());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    // used by health authority
    @PostMapping("/obtain-location-report-ha")
    public SecureLocationReport getLocationHA(@RequestBody SecureObtainLocationRequest secureRequest){
        try {
            int userId = secureRequest.get_request().get_userId();
            int epoch = secureRequest.get_request().get_epoch();
            secureRequest.verify( _serverApp.getHAPublicKey() );

            DBLocationReport dbLocationReport = _reportRepository.findReportByEpochAndUser(userId, epoch);
            if (dbLocationReport == null) return null; // FIXME exception
            LocationReport report = new LocationReport(dbLocationReport);

            // encrypt using HA public key, sign using server private key
            return new SecureLocationReport(report, _serverApp.getHAPublicKey(), _serverApp.getPrivateKey());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    @PostMapping("/users")
    public SecureUsersList getUsers(@RequestBody SecureObtainUsersRequest secureRequest){
        try {
            int x = secureRequest.get_request().get_x();
            int y = secureRequest.get_request().get_y();
            int epoch = secureRequest.get_request().get_epoch();
            secureRequest.verify( _serverApp.getHAPublicKey() );

            List<DBLocationReport> dbLocationReports = _reportRepository.findUsersByLocationAndEpoch(epoch, x, y);
            int userCount = dbLocationReports.size();
            if (userCount == 0) return null;
            Integer[] users = new Integer[userCount];
            for (int i = 0; i < userCount; i++) {
                users[i] = dbLocationReports.get(i).get_userId();
            }

            return new SecureUsersList(users, _serverApp.getHAPublicKey(), _serverApp.getPrivateKey());

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

}
