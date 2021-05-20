package pt.tecnico.sec.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.sec.client.report.LocationProof;
import pt.tecnico.sec.client.report.LocationReport;
import pt.tecnico.sec.keys.CryptoRSA;
import pt.tecnico.sec.server.database.DBLocationReport;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignedLocationReport {

    private LocationReport _report;
    private String _signature;

    public SignedLocationReport() {}

    public SignedLocationReport(LocationReport report, String signature) {
        _report = report;
        _signature = signature;
    }

    public SignedLocationReport(LocationReport report, PrivateKey signKey) throws Exception {
        _report = report;

        // sign message with given private sign key
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(report);
        _signature = CryptoRSA.sign(bytes, signKey);
    }

    // convert from server version
    public SignedLocationReport(DBLocationReport dbLocationReport) {
        _report = new LocationReport(dbLocationReport);
        _signature = dbLocationReport.get_signature();
    }

    // convert from bytes
    public static SignedLocationReport getFromBytes(byte[] reportBytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(reportBytes, SignedLocationReport.class);
    }

    public LocationReport get_report() {
        return _report;
    }

    public void set_report(LocationReport _report) {
        this._report = _report;
    }

    public String get_signature() {
        return _signature;
    }

    public void set_signature(String _signature) {
        this._signature = _signature;
    }

    public int get_userId() {
        return _report.get_userId();
    }

    public int get_epoch() {
        return _report.get_epoch();
    }

    public LocationProof get_witness_proof(int id) {
        return _report.get_witness_proof(id);
    }

    public void verify(PublicKey verifyKey) throws Exception {
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(_report);
        if (_signature == null || !CryptoRSA.verify(bytes, _signature, verifyKey))
            throw new IllegalArgumentException("Signature verify failed!");
    }

    public void verifyProofs(List<PublicKey> clientKeys) throws Exception {
        _report.verifyProofs(clientKeys);
    }

    @Override
    public String toString() {
        return "SignedLocationReport{" +
                "_report=" + _report +
                ", _signature='" + _signature + '\'' +
                '}';
    }
}
