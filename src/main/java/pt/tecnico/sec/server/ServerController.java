package pt.tecnico.sec.server;

import org.springframework.beans.factory.annotation.Autowired;
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

    public SecureMessage secureOKMessage(int senderId) throws Exception {
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(OK);
        return _serverApp.cipherAndSignMessage(senderId, bytes);
    }

    @PostMapping("/obtain-location-report")
    public SecureMessage getLocationClient(@RequestBody SecureMessage secureRequest) {
        try {
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

        } catch (Exception e) {
            return _serverApp.cipherAndSignException(secureRequest.get_senderId(), e);
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
            return _serverApp.cipherAndSignException(secureMessage.get_senderId(), e);
        }
    }

    @PostMapping("/request-proofs")
    public SecureMessage getWitnessProofs(@RequestBody SecureMessage secureRequest) {
        try {
            // decipher and verify request
            byte[] messageBytes = _serverApp.decipherAndVerifyMessage(secureRequest);
            WitnessProofsRequest request = WitnessProofsRequest.getFromBytes(messageBytes);
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
            byte[] bytes = ObjectMapperHandler.writeValueAsBytes(locationProofs);
            return _serverApp.cipherAndSignMessage(secureRequest.get_senderId(), bytes);

        } catch (Exception e) {
            return _serverApp.cipherAndSignException(secureRequest.get_senderId(), e);
        }
    }

    /* ========================================================== */
    /* ====[                 Health Authority               ]==== */
    /* ========================================================== */

    @SuppressWarnings("EqualsBetweenInconvertibleTypes")
    @PostMapping("/users") // FIXME : regular operation? ou pode ser atomic? perguntar
    public SecureMessage getUsers(@RequestBody SecureMessage secureRequest) {
        try {
            // decipher and verify request
            byte[] messageBytes = _serverApp.decipherAndVerifyMessage(secureRequest);
            ObtainUsersRequest request = ObtainUsersRequest.getFromBytes(messageBytes);
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
            UsersAtLocation response = new UsersAtLocation(loc, ep, reports);
            byte[] bytes = ObjectMapperHandler.writeValueAsBytes(response);
            return _serverApp.cipherAndSignMessage(secureRequest.get_senderId(), bytes);

        } catch (Exception e) {
            return _serverApp.cipherAndSignException(secureRequest.get_senderId(), e);
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
            byte[] bytes = ObjectMapperHandler.writeValueAsBytes(OK);
            return _serverApp.cipherAndSignKeyResponse(secureMessage.get_senderId(), bytes);

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
            return _serverApp.cipherAndSignException(senderId, e);
        }
    }


    /* ========================================================== */
    /* ====[              Double Echo Broadcast             ]==== */
    /* ========================================================== */

    @PostMapping("/broadcast-send")
    public SecureMessage doubleEchoBroadcastSend(@RequestBody SecureMessage message) {
        int senderId = message.get_senderId();
        try {
            System.out.println("[*] Received a @SEND Request from " + senderId);
            BroadcastMessage m = _serverApp.decipherAndVerifyBroadcastMessage(message);
            BroadcastService b = _serverApp.getBroadcastService(m.get_broadcastId());
            b.broadcastSENDDeliver(m);
            return secureOKMessage(senderId);
        } catch (Exception e) {
            return _serverApp.cipherAndSignException(senderId, e);
        }
    }

    @PostMapping("/broadcast-echo")
    public SecureMessage doubleEchoBroadcastEcho(@RequestBody SecureMessage secureMessage) {
        int senderId = secureMessage.get_senderId();
        try {
            System.out.println("[*] Received an @ECHO Request from " + senderId);
            BroadcastMessage m = _serverApp.decipherAndVerifyBroadcastMessage(secureMessage);
            BroadcastService b = _serverApp.getBroadcastService(m.get_broadcastId());
            b.broadcastECHODeliver(secureMessage.get_senderId() - 1000, m);
            return secureOKMessage(senderId);

        } catch (Exception e) {
            return _serverApp.cipherAndSignException(senderId, e);
        }
    }

    @PostMapping("/broadcast-ready")
    public SecureMessage doubleEchoBroadcastReady(@RequestBody SecureMessage secureMessage) {
        int senderId = secureMessage.get_senderId();
        try {
            System.out.println("[*] Received a @READY Request from " + senderId);
            BroadcastMessage m = _serverApp.decipherAndVerifyBroadcastMessage(secureMessage);
            BroadcastService b = _serverApp.getBroadcastService(m.get_broadcastId());
            boolean delivered = b.broadcastREADYDeliver(secureMessage.get_senderId()-1000, m);
            if (!delivered) return secureOKMessage(senderId);

            // Deliver
            BroadcastMessage response;
            if (m.is_write()) {
                int timestamp = writeLocationReport(m);
                response = new BroadcastMessage(m.get_broadcastId(), timestamp);
            } else if (m.is_read()) {
                DBLocationReport report = readLocationReport(m);
                response = new BroadcastMessage(m.get_broadcastId(), report);
            } else throw new IllegalArgumentException("Broadcast messages must be reads or writes.");

            // Encrypt and send response
            byte[] bytes = ObjectMapperHandler.writeValueAsBytes(response);
            byte[] responseBytes = _serverApp.postToServer(m.get_originalId() - 1000, bytes, "/broadcast-deliver");
            _serverApp.handleBroadcastMessageResponse(responseBytes);
            return secureOKMessage(senderId);

        } catch (Exception e) {
            return _serverApp.cipherAndSignException(senderId, e);
        }
    }

    @PostMapping("/broadcast-deliver")
    public SecureMessage doubleEchoBroadcastDeliver(@RequestBody SecureMessage secureMessage) {
        int senderId = secureMessage.get_senderId();
        try {
            System.out.println("[*] Received a @DELIVER Request from " + senderId);
            BroadcastMessage m = _serverApp.decipherAndVerifyBroadcastMessage(secureMessage);
            _serverApp.broadcastDeliver(senderId - 1000, m);
            return secureOKMessage(senderId);

        } catch (Exception e) {
            return _serverApp.cipherAndSignException(senderId, e);
        }
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
