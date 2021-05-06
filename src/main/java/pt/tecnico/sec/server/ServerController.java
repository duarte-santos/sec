package pt.tecnico.sec.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pt.tecnico.sec.client.*;
import pt.tecnico.sec.healthauthority.ObtainUsersRequest;
import pt.tecnico.sec.healthauthority.UsersAtLocation;
import pt.tecnico.sec.server.exception.InvalidSignatureException;
import pt.tecnico.sec.server.exception.RecordAlreadyExistsException;
import pt.tecnico.sec.server.exception.ReportNotAcceptableException;

import javax.crypto.SecretKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

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

    public SecureMessage getLocation(SecureMessage secureRequest, boolean fromHA) throws Exception {
        ObtainLocationRequest request;
        SecretKey secretKey;
        try {
            // decipher and verify request
            request = _serverApp.decipherAndVerifyLocationRequest(secureRequest, fromHA);
            secretKey = secureRequest.getSecretKey(_serverApp.getPrivateKey());
        }
        catch (Exception e) {
            throw new InvalidSignatureException("Invalid signature");
        }

        // find requested report
        DBLocationReport dbLocationReport = _reportRepository.findReportByEpochAndUser(request.get_userId(), request.get_epoch());
        if (dbLocationReport == null)
            return null;
        SignedLocationReport report = new SignedLocationReport(dbLocationReport);

        // encrypt using same secret key and client/HA public key, sign using server private key
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes = objectMapper.writeValueAsBytes(report);
        PublicKey cipherKey = fromHA ? _serverApp.getHAPublicKey() : _serverApp.getClientPublicKey(report.get_userId());
        return new SecureMessage(bytes, secretKey, cipherKey, _serverApp.getPrivateKey());
    }

    /* ========================================================== */
    /* ====[                      Users                     ]==== */
    /* ========================================================== */

    @PostMapping("/submit-location-report")
    public void reportLocation(@RequestBody SecureMessage secureMessage) {
        DBLocationReport locationReport;
        try {
            // Decipher and check signatures
            locationReport = _serverApp.decipherAndVerifyReport(secureMessage);
        }
        catch (ReportNotAcceptableException e) {
            throw e;
        }
        catch (Exception e) {
            throw new InvalidSignatureException("Invalid signature");
        }

        // Check if already exists a report with the same userId and epoch
        int userId = locationReport.get_userId();
        int epoch = locationReport.get_epoch();
        if (_reportRepository.findReportByEpochAndUser(userId, epoch) != null)
            throw new RecordAlreadyExistsException("Report for userId " + userId + " and epoch " + epoch + " already exists.");

        // Save report in database
        System.out.println(locationReport);
        _reportRepository.save(locationReport);
    }

    @PostMapping("/obtain-location-report")
    public SecureMessage getLocationClient(@RequestBody SecureMessage secureRequest) throws Exception {
        return getLocation(secureRequest, false);
    }

    @PostMapping("/request-proofs")
    public SecureMessage getWitnessProofs(@RequestBody SecureMessage secureRequest) throws Exception {
        WitnessProofsRequest request;
        SecretKey secretKey;
        try {
            // decipher and verify request
            request = _serverApp.decipherAndVerifyProofsRequest(secureRequest);
            secretKey = secureRequest.getSecretKey(_serverApp.getPrivateKey());
        }
        catch (Exception e) {
            throw new InvalidSignatureException("Invalid signature");
        }

        // find requested reports
        int witnessId = request.get_userId();
        List<DBLocationReport> dbLocationReports = new ArrayList<>();
        for (int epoch : request.get_epochs())
            dbLocationReports.addAll( _reportRepository.findReportsByEpochAndWitness(witnessId, epoch) );

        // convert reports to client form
        List<SignedLocationReport> reports = new ArrayList<>();
        for (DBLocationReport dbLocationReport : dbLocationReports)
            reports.add( new SignedLocationReport(dbLocationReport) );

        // extract requested proofs
        List<LocationProof> locationProofs = new ArrayList<>();
        for (SignedLocationReport report : reports)
            locationProofs.add( report.get_witness_proof(witnessId) );

        // encrypt using same secret key and client public key, sign using server private key
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes = objectMapper.writeValueAsBytes(locationProofs);
        PublicKey cipherKey = _serverApp.getClientPublicKey(witnessId);
        return new SecureMessage(bytes, secretKey, cipherKey, _serverApp.getPrivateKey());
    }

    /* ========================================================== */
    /* ====[                 Health Authority               ]==== */
    /* ========================================================== */

    @PostMapping("/obtain-location-report-ha")
    public SecureMessage getLocationHA(@RequestBody SecureMessage secureRequest) throws Exception {
        return getLocation(secureRequest, true);
    }

    @PostMapping("/users")
    public SecureMessage getUsers(@RequestBody SecureMessage secureRequest) throws Exception {
        ObtainUsersRequest request;
        SecretKey secretKey;
        try {
            // decipher and verify request
            request = _serverApp.decipherAndVerifyHAUsersRequest(secureRequest);
            secretKey = secureRequest.getSecretKey(_serverApp.getPrivateKey());
        }
        catch (Exception e) {
            throw new InvalidSignatureException("Invalid signature");
        }

        // find requested reports
        Location location = request.get_location();
        int epoch = request.get_epoch();
        List<DBLocationReport> dbLocationReports = _reportRepository.findUsersByLocationAndEpoch(epoch, location.get_x(), location.get_y());

        // convert reports to client form
        List<SignedLocationReport> reports = new ArrayList<>();
        for (DBLocationReport dbLocationReport : dbLocationReports)
            reports.add( new SignedLocationReport(dbLocationReport) );
        UsersAtLocation response = new UsersAtLocation(location, epoch, reports);

        // encrypt using HA public key, sign using server private key
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes = objectMapper.writeValueAsBytes(response);
        return new SecureMessage(bytes, secretKey, _serverApp.getHAPublicKey(), _serverApp.getPrivateKey());
    }

}
