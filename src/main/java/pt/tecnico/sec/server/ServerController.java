package pt.tecnico.sec.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pt.tecnico.sec.client.report.Location;
import pt.tecnico.sec.client.report.LocationProof;
import pt.tecnico.sec.contract.*;
import pt.tecnico.sec.contract.exception.RecordAlreadyExistsException;
import pt.tecnico.sec.server.broadcast.BroadcastMessage;
import pt.tecnico.sec.server.broadcast.BroadcastService;
import pt.tecnico.sec.server.database.DBLocationProof;
import pt.tecnico.sec.server.database.DBLocationReport;

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

    public SecureMessage secureOKMessage(int senderId) throws Exception {
        Message m = new Message(OK);
        return _serverApp.cipherAndSignMessage(senderId, m);
    }

    public SecureMessage secureExceptionMessage(int senderId, Exception exception) {
        try {
            Message m = new Message(exception);
            return _serverApp.cipherAndSignMessage(senderId, m);
        } catch (Exception e) { return null; }
    }

    @PostMapping("/obtain-location-report")
    public SecureMessage getLocationClient(@RequestBody SecureMessage secureRequest) {
        try {
            // decipher and verify request
            Message message = _serverApp.decipherAndVerifyMessage(secureRequest);
            ObtainLocationRequest request = message.retrieveObtainLocationRequest();
            request.checkSender( secureRequest.get_senderId() );

            // broadcast Read operation
            _serverApp.refreshServerSecretKeys();
            DBLocationReport dbLocationReport = _serverApp.atomicBroadcastR(request);
            SignedLocationReport report;
            if (dbLocationReport == null) report = null;
            else report = new SignedLocationReport(dbLocationReport);

            // encrypt using same secret key and client/HA public key, sign using server private key
            Message response = new Message(report);
            return _serverApp.cipherAndSignMessage(secureRequest.get_senderId(), response);

        } catch (Exception e) {
            return secureExceptionMessage(secureRequest.get_senderId(), e);
        }
    }

    /* ========================================================== */
    /* ====[                      Users                     ]==== */
    /* ========================================================== */

    @PostMapping("/submit-location-report")
    public SecureMessage reportLocation(@RequestBody SecureMessage secureMessage) {
        try {
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
            return secureOKMessage(secureMessage.get_senderId());

        } catch (Exception e) {
            return secureExceptionMessage(secureMessage.get_senderId(), e);
        }
    }

    @PostMapping("/request-proofs")
    public SecureMessage getWitnessProofs(@RequestBody SecureMessage secureRequest) {
        try {
            // decipher and verify request
            Message message = _serverApp.decipherAndVerifyMessage(secureRequest);
            WitnessProofsRequest request = message.retrieveWitnessProofsRequest();
            request.checkSender( secureRequest.get_senderId() );

            int witnessId = request.get_userId();
            Set<Integer> epochs = request.get_epochs();
            List<LocationProof> locationProofs = new ArrayList<>();

            _serverApp.refreshServerSecretKeys();

            // set of read operations
            int userCount = _serverApp.getUserCount();
            for (int id = 0; id < userCount; id++) {
                if (id == witnessId) continue; // user will never be a witness of itself
                for (int ep : epochs) {
                    ObtainLocationRequest userReportRequest = new ObtainLocationRequest(id, ep);
                    DBLocationReport dbLocationReport = _serverApp.broadcastR(userReportRequest);

                    // filter requested reports
                    if (dbLocationReport == null) continue;
                    DBLocationProof proof = dbLocationReport.get_witness_proof(witnessId);
                    if (proof != null)
                        locationProofs.add(new LocationProof(proof));
                }
            }

            // encrypt and send response
            Message response = new Message(locationProofs);
            return _serverApp.cipherAndSignMessage(secureRequest.get_senderId(), response);

        } catch (Exception e) {
            return secureExceptionMessage(secureRequest.get_senderId(), e);
        }
    }

    /* ========================================================== */
    /* ====[                 Health Authority               ]==== */
    /* ========================================================== */

    @SuppressWarnings("EqualsBetweenInconvertibleTypes")
    @PostMapping("/users")
    public SecureMessage getUsers(@RequestBody SecureMessage secureRequest) {
        try {
            // decipher and verify request
            Message message = _serverApp.decipherAndVerifyMessage(secureRequest);
            ObtainUsersRequest request = message.retrieveObtainUsersRequest();
            request.checkSender(secureRequest.get_senderId());

            int ep = request.get_epoch();
            Location loc = request.get_location();
            List<SignedLocationReport> reports = new ArrayList<>();

            _serverApp.refreshServerSecretKeys();

            // set of read operations
            int userCount = _serverApp.getUserCount();
            for (int id = 0; id < userCount; id++) {
                ObtainLocationRequest userReportRequest = new ObtainLocationRequest(id, ep);
                DBLocationReport dbLocationReport = _serverApp.broadcastR(userReportRequest);
                // filter requested reports
                if (dbLocationReport != null && dbLocationReport.get_location().equals(loc))
                    reports.add(new SignedLocationReport(dbLocationReport));
            }

            // encrypt and send response
            Message response = new Message(new UsersAtLocation(loc, ep, reports) );
            return _serverApp.cipherAndSignMessage(secureRequest.get_senderId(), response);

        } catch (Exception e) {
            return secureExceptionMessage(secureRequest.get_senderId(), e);
        }
    }


    /* ========================================================== */
    /* ====[               Handle Secret Keys               ]==== */
    /* ========================================================== */

    @PostMapping("/secret-key")
    public SecureMessage newSecretKey(@RequestBody SecureMessage secureMessage) {
        try {
            // decipher and verify message
            SecretKey newSecretKey = _serverApp.decipherAndVerifyKey(secureMessage);

            // save new key
            _serverApp.saveSecretKey(secureMessage.get_senderId(), newSecretKey);

            // Send secure response
            Message response = new Message(OK);
            return _serverApp.cipherAndSignKeyResponse(secureMessage.get_senderId(), response);

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return _serverApp.cipherAndSignKeyException(secureMessage.get_senderId(), e);
        }
    }

    @PostMapping("/refresh-secret-keys")
    public SecureMessage refreshSecretKeys(@RequestBody SecureMessage message) {
        int senderId = message.get_senderId(); // empty message, only contains sender
        try {
            // send refresh request to other servers
            _serverApp.refreshServerSecretKeys();
            return secureOKMessage(senderId);
        } catch (Exception e) {
            return secureExceptionMessage(senderId, e);
        }
    }


    /* ========================================================== */
    /* ====[              Double Echo Broadcast             ]==== */
    /* ========================================================== */

    @PostMapping("/broadcast-send")
    public SecureMessage broadcastSend(@RequestBody SecureMessage message) {
        int senderId = message.get_senderId();
        try {
            System.out.println("[*] Received a @SEND Request from " + senderId);
            BroadcastMessage m = _serverApp.decipherAndVerifyBroadcastMessage(message);
            BroadcastService b = _serverApp.getBroadcastService(m.get_broadcastId());
            b.broadcastSENDDeliver(m);
            return secureOKMessage(senderId);
        } catch (Exception e) {
            return secureExceptionMessage(senderId, e);
        }
    }

    @PostMapping("/broadcast-echo")
    public SecureMessage broadcastEcho(@RequestBody SecureMessage secureMessage) {
        int senderId = secureMessage.get_senderId();
        try {
            System.out.println("[*] Received an @ECHO Request from " + senderId);
            BroadcastMessage m = _serverApp.decipherAndVerifyBroadcastMessage(secureMessage);
            BroadcastService b = _serverApp.getBroadcastService(m.get_broadcastId());
            b.broadcastECHODeliver(secureMessage.get_senderId() - 1000, m);
            return secureOKMessage(senderId);

        } catch (Exception e) {
            return secureExceptionMessage(senderId, e);
        }
    }

    @PostMapping("/broadcast-ready")
    public SecureMessage broadcastReady(@RequestBody SecureMessage secureMessage) {
        int senderId = secureMessage.get_senderId();
        try {
            System.out.println("[*] Received a @READY Request from " + senderId);
            BroadcastMessage m = _serverApp.decipherAndVerifyBroadcastMessage(secureMessage);
            BroadcastService b = _serverApp.getBroadcastService(m.get_broadcastId());
            boolean delivered = b.broadcastREADYDeliver(secureMessage.get_senderId()-1000, m);
            if (!delivered) return secureOKMessage(senderId);

            // Deliver
            BroadcastMessage deliver;
            if (m.is_write()) {
                int timestamp = writeLocationReport(m);
                deliver = new BroadcastMessage(m.get_broadcastId(), timestamp);
            } else if (m.is_read()) {
                DBLocationReport report = readLocationReport(m);
                deliver = new BroadcastMessage(m.get_broadcastId(), report);
            } else throw new IllegalArgumentException("Broadcast messages must be reads or writes.");

            // Encrypt and send response
            Message response = _serverApp.postBroadcastToServer(m.get_originalId() - 1000, deliver, "/broadcast-deliver");
            _serverApp.handleBroadcastMessageResponse(response);
            return secureOKMessage(senderId);

        } catch (Exception e) {
            return secureExceptionMessage(senderId, e);
        }
    }

    @PostMapping("/broadcast-deliver")
    public SecureMessage broadcastDeliver(@RequestBody SecureMessage secureMessage) {
        int senderId = secureMessage.get_senderId();
        try {
            System.out.println("[*] Received a @DELIVER Request from " + senderId);
            BroadcastMessage m = _serverApp.decipherAndVerifyBroadcastMessage(secureMessage);
            _serverApp.broadcastDeliver(senderId - 1000, m);
            return secureOKMessage(senderId);

        } catch (Exception e) {
            return secureExceptionMessage(senderId, e);
        }
    }


    /* ========================================================== */
    /* ====[                 Access Database                ]==== */
    /* ========================================================== */

    public int writeLocationReport(BroadcastMessage m) {
        // Decipher and check report
        m.checkOrigin();
        DBLocationReport locationReport = m.getDBLocationReport();
        _serverApp.verifyDBReport(locationReport);

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
