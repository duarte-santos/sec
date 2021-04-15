package pt.tecnico.sec.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pt.tecnico.sec.client.Location;
import pt.tecnico.sec.client.LocationReport;
import pt.tecnico.sec.client.ObtainLocationRequest;
import pt.tecnico.sec.client.SecureMessage;
import pt.tecnico.sec.healthauthority.ObtainUsersRequest;
import pt.tecnico.sec.healthauthority.UsersAtLocation;
import pt.tecnico.sec.server.exception.RecordAlreadyExistsException;

import javax.crypto.SecretKey;
import java.security.PublicKey;
import java.util.ArrayList;
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
    public SecureMessage getLocationClient(@RequestBody SecureMessage secureRequest){
        return getLocation(secureRequest, false);
    }

    // used by health authority
    @PostMapping("/obtain-location-report-ha")
    public SecureMessage getLocationHA(@RequestBody SecureMessage secureRequest){
        return getLocation(secureRequest, true);
    }

    public SecureMessage getLocation(SecureMessage secureRequest, boolean fromHA) {
        try {
            // decipher and verify request
            ObtainLocationRequest request = _serverApp.decipherAndVerifyRequest(secureRequest, fromHA);
            SecretKey secretKey = secureRequest.getSecretKey( _serverApp.getPrivateKey() );

            // find requested report
            DBLocationReport dbLocationReport = _reportRepository.findReportByEpochAndUser(request.get_userId(), request.get_epoch());
            if (dbLocationReport == null)
                return null; // FIXME exception
            LocationReport report = new LocationReport(dbLocationReport);

            // encrypt using same secret key and client public key, sign using server private key
            ObjectMapper objectMapper = new ObjectMapper();
            byte[] bytes = objectMapper.writeValueAsBytes(report);
            PublicKey cipherKey = fromHA ? _serverApp.getHAPublicKey() : _serverApp.getClientPublicKey(report.get_userId());
            return new SecureMessage(bytes, secretKey, cipherKey, _serverApp.getPrivateKey());

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    @PostMapping("/users")
    public SecureMessage getUsers(@RequestBody SecureMessage secureRequest){
        try {
            ObtainUsersRequest request = _serverApp.decipherAndVerifyHAUsersRequest(secureRequest);
            SecretKey secretKey = secureRequest.getSecretKey( _serverApp.getPrivateKey() );

            Location location = request.get_location();
            int epoch = request.get_epoch();
            List<DBLocationReport> dbLocationReports = _reportRepository.findUsersByLocationAndEpoch(epoch, location.get_x(), location.get_y());

            List<Integer> users = new ArrayList<>();
            for (DBLocationReport dbLocationReport : dbLocationReports)
                users.add(dbLocationReport.get_userId());
            UsersAtLocation userIds = new UsersAtLocation(location, epoch, users);

            // encrypt using HA public key, sign using server private key
            ObjectMapper objectMapper = new ObjectMapper();
            byte[] bytes = objectMapper.writeValueAsBytes(userIds);
            return new SecureMessage(bytes, secretKey, _serverApp.getHAPublicKey(), _serverApp.getPrivateKey());

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

}
