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

    @PostMapping("/obtain-location-report")
    public SecureMessage getLocationClient(@RequestBody SecureMessage secureRequest) throws Exception {
        // decipher and verify request
        ObtainLocationRequest request = _serverApp.decipherAndVerifyReportRequest(secureRequest, false);

        // broadcast Read operation
        DBLocationReport dbLocationReport = _serverApp.broadcastRead(request);
        if (dbLocationReport == null)
            return null;

        SignedLocationReport report = new SignedLocationReport(dbLocationReport);

        // encrypt using same secret key and client/HA public key, sign using server private key
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(report);
        return _serverApp.cipherAndSignMessage(secureRequest.get_senderId(), bytes, false);
    }


    /* ========================================================== */
    /* ====[               Handle Secret Keys               ]==== */
    /* ========================================================== */

    @PostMapping("/secret-key")
    public SecureMessage newClientSecretKey(@RequestBody SecureMessage secureMessage) throws Exception {
        // decipher and verify message
        SecretKey newSecretKey = _serverApp.decipherAndVerifyKey(secureMessage, false);

        // save new key
        _serverApp.saveClientSecretKey(secureMessage.get_senderId(), newSecretKey);

        // Send secure response
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(OK);
        return _serverApp.cipherAndSignMessage(secureMessage.get_senderId(), bytes, false);
    }

    @PostMapping("/secret-key-server")
    public SecureMessage newServerSecretKey(@RequestBody SecureMessage secureMessage) throws Exception {
        // decipher and verify message
        SecretKey newSecretKey = _serverApp.decipherAndVerifyKey(secureMessage, true);

        // save new key
        _serverApp.saveServerSecretKey(secureMessage.get_senderId(), newSecretKey);

        // Send secure response
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(OK);
        return _serverApp.cipherAndSignMessage(secureMessage.get_senderId(), bytes, true);
    }


    /* ========================================================== */
    /* ====[                      Users                     ]==== */
    /* ========================================================== */

    @PostMapping("/submit-location-report")
    public SecureMessage reportLocation(@RequestBody SecureMessage secureMessage) throws Exception {
        // Decipher and check report
        DBLocationReport locationReport = _serverApp.decipherAndVerifyReport(secureMessage, false);

        // Check if already exists a report with the same userId and epoch
        int userId = locationReport.get_userId();
        int epoch = locationReport.get_epoch();
        if (_reportRepository.findReportByEpochAndUser(userId, epoch) != null)
            throw new RecordAlreadyExistsException("Report for userId " + userId + " and epoch " + epoch + " already exists.");

        // Broadcast write operation to other servers
        _serverApp.broadcastWrite(locationReport);

        // Send secure response
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(OK);
        return _serverApp.cipherAndSignMessage(secureMessage.get_senderId(), bytes, false);
    }

    @PostMapping("/request-proofs")
    public SecureMessage getWitnessProofs(@RequestBody SecureMessage secureRequest) throws Exception {
        // decipher and verify request
        WitnessProofsRequest request = _serverApp.decipherAndVerifyProofsRequest(secureRequest, false);
        int witnessId = request.get_userId();
        Set<Integer> epochs = request.get_epochs();

        List<LocationProof> locationProofs = new ArrayList<>();

        // set of read operations
        int userCount = _serverApp.getUserCount();
        for (int id = 0; id < userCount; id++) {
            if (id == witnessId) continue; // user will never be a witness of itself
            for (int ep : epochs) {
                ObtainLocationRequest userReportRequest = new ObtainLocationRequest(id, ep);
                DBLocationReport dbLocationReport = _serverApp.broadcastRead(userReportRequest);

                // filter requested reports
                if (dbLocationReport == null) continue;
                DBLocationProof proof = dbLocationReport.get_witness_proof(witnessId);
                if (proof != null)
                    locationProofs.add(new LocationProof(proof));
            }
        }

        // encrypt and send response
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(locationProofs);
        return _serverApp.cipherAndSignMessage(secureRequest.get_senderId(), bytes, false);

    }

    /* ========================================================== */
    /* ====[                 Health Authority               ]==== */
    /* ========================================================== */

    @SuppressWarnings("EqualsBetweenInconvertibleTypes")
    @PostMapping("/users") // FIXME regular operation? ou pode ser atomic? perguntar
    public SecureMessage getUsers(@RequestBody SecureMessage secureRequest) throws Exception {
        // decipher and verify request
        ObtainUsersRequest request = _serverApp.decipherAndVerifyUsersRequest(secureRequest, false);
        int ep = request.get_epoch();
        Location loc = request.get_location();

        List<SignedLocationReport> reports = new ArrayList<>();

        // set of read operations
        int userCount = _serverApp.getUserCount();
        for (int id = 0; id < userCount; id++) {
            ObtainLocationRequest userReportRequest = new ObtainLocationRequest(id, ep);
            DBLocationReport dbLocationReport = _serverApp.broadcastRead(userReportRequest);
            // filter requested reports
            if (dbLocationReport != null && dbLocationReport.get_location().equals(loc))
                reports.add(new SignedLocationReport(dbLocationReport));
        }

        // encrypt and send response
        UsersAtLocation response = new UsersAtLocation(loc, ep, reports);
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(response);
        return _serverApp.cipherAndSignMessage(secureRequest.get_senderId(), bytes, false);
    }

    /* ========================================================== */
    /* ====[                Atomic Registers                ]==== */
    /* ========================================================== */

    @PostMapping("/broadcast-write")
    public SecureMessage broadcastWrite(@RequestBody SecureMessage secureMessage) throws Exception {
        System.out.println("Received Write broadcast");

        // Decipher and check report
        DBLocationReport locationReport = _serverApp.decipherAndVerifyDBReport(secureMessage, true);
        int senderId = secureMessage.get_senderId();
        if (senderId != _serverApp.getId()) _serverApp.serverSecretKeyUsed(senderId);

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

        // Encrypt and send response
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(timestamp);
        return _serverApp.cipherAndSignMessage(senderId, bytes, true);
    }

    @PostMapping("/broadcast-read")
    public SecureMessage broadcastRead(@RequestBody SecureMessage secureRequest) throws Exception {
        System.out.println("Received Read broadcast");

        // decipher and verify request TODO use different ids for servers, check sender is server
        ObtainLocationRequest request = _serverApp.decipherAndVerifyReportRequest(secureRequest, true);
        int senderId = secureRequest.get_senderId();
        if (senderId != _serverApp.getId()) _serverApp.serverSecretKeyUsed(senderId);

        // find requested report
        DBLocationReport report = _reportRepository.findReportByEpochAndUser(request.get_userId(), request.get_epoch());

        // encrypt using same secret key and client/HA public key, sign using server private key
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(report);
        return _serverApp.cipherAndSignMessage(senderId, bytes, true);
    }


}
