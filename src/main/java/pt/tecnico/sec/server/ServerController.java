package pt.tecnico.sec.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pt.tecnico.sec.client.LocationReport;
import pt.tecnico.sec.client.ObtainLocationRequest;
import pt.tecnico.sec.client.SecureMessage;
import pt.tecnico.sec.healthauthority.SecureObtainUsersRequest;
import pt.tecnico.sec.healthauthority.SecureUsersList;
import pt.tecnico.sec.server.exception.RecordAlreadyExistsException;

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


    @PostMapping("/location-report")
    public void reportLocation(@RequestBody SecureMessage secureMessage) throws RecordAlreadyExistsException, Exception {
        // Decipher and check signatures
        LocationReport locationReport = _serverApp.decipherAndVerifyReport(secureMessage);

        // Check if already exists a report with the same userId and epoch
        int userId = locationReport.get_userId();
        int epoch = locationReport.get_epoch();
        if (_reportRepository.findReportByEpochAndUser(userId, epoch) != null)
            throw new RecordAlreadyExistsException("Report for userId " + userId + " and epoch " + epoch + " already exists.");

        // Save report in database
        System.out.println(locationReport);
        DBLocationReport report = new DBLocationReport(locationReport);
        _reportRepository.save(report);
    }

    // used by clients
    @PostMapping("/obtain-location-report")
    public SecureMessage getLocation(@RequestBody SecureMessage secureMessage){
        try {
            ObtainLocationRequest request = _serverApp.decipherAndVerifyRequest(secureMessage);

            DBLocationReport dbLocationReport = _reportRepository.findReportByEpochAndUser(request.get_userId(), request.get_epoch());
            if (dbLocationReport == null)
                return null; // FIXME exception
            LocationReport report = new LocationReport(dbLocationReport);

            // encrypt using client public key, sign using server private key
            PublicKey clientKey = _serverApp.getClientPublicKey(report.get_userId());
            ObjectMapper objectMapper = new ObjectMapper();
            byte[] bytes = objectMapper.writeValueAsBytes(report);
            return new SecureMessage(bytes, clientKey, _serverApp.getPrivateKey());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    // used by health authority
    @PostMapping("/obtain-location-report-ha")
    public SecureMessage getLocationHA(@RequestBody SecureMessage secureMessage){
        try {
            ObtainLocationRequest request = _serverApp.decipherAndVerifyHARequest(secureMessage);

            DBLocationReport dbLocationReport = _reportRepository.findReportByEpochAndUser(request.get_userId(), request.get_epoch());
            if (dbLocationReport == null) return null; // FIXME exception
            LocationReport report = new LocationReport(dbLocationReport);

            // encrypt using HA public key, sign using server private key
            ObjectMapper objectMapper = new ObjectMapper();
            byte[] bytes = objectMapper.writeValueAsBytes(report);
            return new SecureMessage(bytes, _serverApp.getHAPublicKey(), _serverApp.getPrivateKey());
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
            //secureRequest.verify( _serverApp.getHAPublicKey() );

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
