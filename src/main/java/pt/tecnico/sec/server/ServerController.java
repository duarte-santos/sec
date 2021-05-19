package pt.tecnico.sec.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pt.tecnico.sec.ObjectMapperHandler;
import pt.tecnico.sec.client.*;
import pt.tecnico.sec.healthauthority.ObtainUsersRequest;
import pt.tecnico.sec.healthauthority.UsersAtLocation;
import pt.tecnico.sec.server.exception.RecordAlreadyExistsException;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static pt.tecnico.sec.Constants.OK;

@SuppressWarnings("AccessStaticViaInstance")
@RestController
public class ServerController {

    private static ServerApplication _serverApp;
    private static ReportRepository _reportRepository;

    @Autowired
    private ServerController(ServerApplication serverApp, ReportRepository reportRepository) {
        _serverApp = serverApp;
        _reportRepository = reportRepository;
    }

    /* ========================================================== */
    /* ====[                     General                    ]==== */
    /* ========================================================== */

    @PostMapping("/obtain-location-report")
    public SecureMessage getLocationClient(@RequestBody SecureMessage secureRequest) throws Exception {
        // decipher and verify request
        byte[] messageBytes = _serverApp.decipherAndVerifyMessage(secureRequest);
        ObtainLocationRequest request = ObtainLocationRequest.getFromBytes(messageBytes);
        request.checkSender( secureRequest.get_senderId() );

        // broadcast Read operation
        _serverApp.refreshServerSecretKeys();
        DBLocationReport dbLocationReport = _serverApp.broadcastR(request);
        if (dbLocationReport == null)
            return null;

        SignedLocationReport report = new SignedLocationReport(dbLocationReport);

        // encrypt using same secret key and client/HA public key, sign using server private key
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(report);
        return _serverApp.cipherAndSignMessage(secureRequest.get_senderId(), bytes);
    }

    /* ========================================================== */
    /* ====[                      Users                     ]==== */
    /* ========================================================== */

    @PostMapping("/submit-location-report")
    public SecureMessage reportLocation(@RequestBody SecureMessage secureMessage) throws Exception {
        // Decipher and check report
        DBLocationReport locationReport = _serverApp.decipherAndVerifyReport(secureMessage);

        // Check if already exists a report with the same userId and epoch
        int userId = locationReport.get_userId();
        int epoch = locationReport.get_epoch();
        if (_reportRepository.findReportByEpochAndUser(userId, epoch) != null)
            throw new RecordAlreadyExistsException("Report for userId " + userId + " and epoch " + epoch + " already exists.");

        // Broadcast write operation to other servers
        _serverApp.refreshServerSecretKeys();
        _serverApp.broadcastW(locationReport);

        // Send secure response
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(OK);
        return _serverApp.cipherAndSignMessage(secureMessage.get_senderId(), bytes);
    }

    @PostMapping("/request-proofs")
    public SecureMessage getWitnessProofs(@RequestBody SecureMessage secureRequest) throws Exception {
        // decipher and verify request
        byte[] messageBytes = _serverApp.decipherAndVerifyMessage(secureRequest);
        WitnessProofsRequest request = WitnessProofsRequest.getFromBytes(messageBytes);
        request.checkSender( secureRequest.get_senderId() );

        int witnessId = request.get_userId();
        Set<Integer> epochs = request.get_epochs();
        List<LocationProof> locationProofs = new ArrayList<>();

        // set of read operations
        int userCount = _serverApp.getUserCount();
        for (int id = 0; id < userCount; id++) {
            if (id == witnessId) continue; // user will never be a witness of itself
            for (int ep : epochs) {
                ObtainLocationRequest userReportRequest = new ObtainLocationRequest(id, ep);
                _serverApp.refreshServerSecretKeys();
                DBLocationReport dbLocationReport = _serverApp.broadcastR(userReportRequest);

                // filter requested reports
                if (dbLocationReport == null) continue;
                DBLocationProof proof = dbLocationReport.get_witness_proof(witnessId);
                if (proof != null)
                    locationProofs.add(new LocationProof(proof));
            }
        }

        // encrypt and send response
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(locationProofs);
        return _serverApp.cipherAndSignMessage(secureRequest.get_senderId(), bytes);

    }

    /* ========================================================== */
    /* ====[                 Health Authority               ]==== */
    /* ========================================================== */

    @SuppressWarnings("EqualsBetweenInconvertibleTypes")
    @PostMapping("/users") // FIXME : regular operation? ou pode ser atomic? perguntar
    public SecureMessage getUsers(@RequestBody SecureMessage secureRequest) throws Exception {
        // decipher and verify request
        byte[] messageBytes = _serverApp.decipherAndVerifyMessage(secureRequest);
        ObtainUsersRequest request = ObtainUsersRequest.getFromBytes(messageBytes);
        request.checkSender(secureRequest.get_senderId());

        int ep = request.get_epoch();
        Location loc = request.get_location();
        List<SignedLocationReport> reports = new ArrayList<>();

        // set of read operations
        int userCount = _serverApp.getUserCount();
        for (int id = 0; id < userCount; id++) {
            ObtainLocationRequest userReportRequest = new ObtainLocationRequest(id, ep);
            _serverApp.refreshServerSecretKeys();
            DBLocationReport dbLocationReport = _serverApp.broadcastR(userReportRequest);
            // filter requested reports
            if (dbLocationReport != null && dbLocationReport.get_location().equals(loc))
                reports.add(new SignedLocationReport(dbLocationReport));
        }

        // encrypt and send response
        UsersAtLocation response = new UsersAtLocation(loc, ep, reports);
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(response);
        return _serverApp.cipherAndSignMessage(secureRequest.get_senderId(), bytes);
    }


    /* ========================================================== */
    /* ====[               Handle Secret Keys               ]==== */
    /* ========================================================== */

    @PostMapping("/secret-key")
    public SecureMessage newSecretKey(@RequestBody SecureMessage secureMessage) throws Exception {
        // decipher and verify message
        SecretKey newSecretKey = _serverApp.decipherAndVerifyKey(secureMessage);

        // save new key
        _serverApp.saveSecretKey(secureMessage.get_senderId(), newSecretKey);

        // Send secure response
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(OK);
        return _serverApp.cipherAndSignMessage(secureMessage.get_senderId(), bytes);
    }

    @GetMapping("/refresh-secret-keys")
    public void refreshSecretKeys() throws Exception {
        // send refresh request to other servers
        _serverApp.refreshServerSecretKeys();
    }


    /* ========================================================== */
    /* ====[              Double Echo Broadcast             ]==== */
    /* ========================================================== */

    @PostMapping("/broadcast-send")
    public void doubleEchoBroadcastSend(@RequestBody SecureMessage message) throws Exception {
        int senderId = message.get_senderId();
        System.out.println("[*] Received a @SEND Request from " + senderId);
        BroadcastMessage m = _serverApp.decipherAndVerifyBroadcastMessage(message);
        BroadcastService b = _serverApp.getBroadcastService(m.get_broadcastId());
        b.broadcastSENDDeliver(m);
    }

    @PostMapping("/broadcast-echo")
    public void doubleEchoBroadcastEcho(@RequestBody SecureMessage secureMessage) throws Exception {
        int senderId = secureMessage.get_senderId();
        System.out.println("[*] Received an @ECHO Request from " + senderId);
        BroadcastMessage m = _serverApp.decipherAndVerifyBroadcastMessage(secureMessage);
        BroadcastService b = _serverApp.getBroadcastService(m.get_broadcastId());
        b.broadcastECHODeliver(secureMessage.get_senderId()-1000, m);
    }

    @PostMapping("/broadcast-ready")
    public void doubleEchoBroadcastReady(@RequestBody SecureMessage secureMessage) throws Exception {
        int senderId = secureMessage.get_senderId();
        System.out.println("[*] Received a @READY Request from " + senderId);
        BroadcastMessage m = _serverApp.decipherAndVerifyBroadcastMessage(secureMessage);
        BroadcastService b = _serverApp.getBroadcastService(m.get_broadcastId());
        boolean delivered = b.broadcastREADYDeliver(secureMessage.get_senderId()-1000, m);
        if (!delivered) return;

        // Deliver
        BroadcastMessage response;
        if (m.is_write()) {
            int timestamp = writeLocationReport(m);
            response = new BroadcastMessage(m.get_broadcastId(), timestamp);
        } else if (m.is_read()) {
            DBLocationReport report = readLocationReport(m);
            response = new BroadcastMessage(m.get_broadcastId(), report);
        } else return;

        // Encrypt and send response
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(response);
        _serverApp.postToServer(m.get_originalId() - 1000, bytes, "/broadcast-deliver");
    }

    @PostMapping("/broadcast-deliver")
    public void doubleEchoBroadcastDeliver(@RequestBody SecureMessage secureMessage) throws Exception {
        System.out.println("[*] Received a @DELIVER Request from " + secureMessage.get_senderId());
        BroadcastMessage m = _serverApp.decipherAndVerifyBroadcastMessage(secureMessage);
        _serverApp.broadcastDeliver(secureMessage.get_senderId()-1000, m);
    }


    /* ========================================================== */
    /* ====[                 Access Database                ]==== */
    /* ========================================================== */

    public int writeLocationReport(BroadcastMessage m) throws Exception {
        // Decipher and check report
        m.checkOrigin();
        DBLocationReport locationReport = m.getDBLocationReport();
        _serverApp.checkReportSignatures(new LocationReport(locationReport));

        int epoch = locationReport.get_epoch();
        int userId = locationReport.get_userId();
        int timestamp = locationReport.get_timestamp();
        int mytimestamp = 0;

        // Update database
        DBLocationReport mylocationReport = _reportRepository.findReportByEpochAndUser(userId, epoch);
        if (mylocationReport != null)
            mytimestamp = mylocationReport.get_timestamp();

        if (timestamp > mytimestamp){
            if (mylocationReport != null) _reportRepository.delete(mylocationReport);
            _reportRepository.save(locationReport);
        }

        return timestamp;
    }

    public DBLocationReport readLocationReport(BroadcastMessage m) {
        // Decipher and check request
        m.checkOrigin();
        ObtainLocationRequest locationRequest = m.get_request();

        int epoch = locationRequest.get_epoch();
        int userId = locationRequest.get_userId();

        // Find requested report
        return _reportRepository.findReportByEpochAndUser(userId, epoch);
    }
}
