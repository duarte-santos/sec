package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.server.DBLocationReport;
import pt.tecnico.sec.server.DBProofData;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationReport {

    // constants // FIXME : constantes todas num ficheiro para todos acederem?
    private static final String SUCCESS = "success";
    private static final double DETECTION_RANGE = 2;

    // attributes
    private int _userId;
    private int _epoch;
    private Location _location;
    private List<LocationProof> _proofs = new ArrayList<>();

    public LocationReport() {}

    public LocationReport(int userId, int epoch, Location location, List<LocationProof> proofs) {
        _userId = userId;
        _epoch = epoch;
        _location = location;
        _proofs = proofs;
    }

    // convert from server version
    public LocationReport(DBLocationReport dbLocationReport) {
        _userId = dbLocationReport.get_userId();
        _epoch = dbLocationReport.get_epoch();
        _location = new Location( dbLocationReport.get_location() );
        for (DBProofData dbProof : dbLocationReport.get_DB_proofs())
            _proofs.add(new LocationProof( dbProof ));
    }

    public int get_userId() {
        return _userId;
    }

    public void set_userId(int _userId) {
        this._userId = _userId;
    }

    public Location get_location() {
        return _location;
    }

    public void set_location(Location _location) {
        this._location = _location;
    }

    public List<LocationProof> get_proofs() {
        return _proofs;
    }

    public void set_proofs(List<LocationProof> _proofs) {
        this._proofs = _proofs;
    }

    public int get_epoch() {
        return _epoch;
    }

    public void set_epoch(int _epoch) {
        this._epoch = _epoch;
    }

    public void verify(byte[] signature, PublicKey verifyKey) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] reportBytes = objectMapper.writeValueAsBytes(this);

        // Verify signature
        if (signature == null || !RSAKeyGenerator.verify(reportBytes, signature, verifyKey)) {
            throw new IllegalArgumentException("Report signature failed!"); //FIXME type of exception
        }
    }

    public static PublicKey getClientPublicKey(int clientId) throws GeneralSecurityException, IOException {
        String keyPath = RSAKeyGenerator.KEYS_PATH + clientId + ".pub";
        return RSAKeyGenerator.readPublicKey(keyPath);
    }

    public void verifyProofs() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        for (LocationProof signedProof : _proofs) {
            ProofData proofData = signedProof.get_proofData();
            byte[] data = objectMapper.writeValueAsBytes(proofData);
            byte[] signature = signedProof.get_signature();
            PublicKey clientKey = getClientPublicKey(signedProof.get_witnessId());
            if (signature == null || !RSAKeyGenerator.verify(data, signature, clientKey)
                    || proofData.get_epoch() != _epoch
                    || !isNearby(proofData.get_location())
                    || !proofData.get_type().equals(SUCCESS)
                    || proofData.get_proverId() != _userId) {
                throw new IllegalArgumentException("Proof signature failed!"); //FIXME : discard proof, not report
            }
        }
    }

    private boolean isNearby(Location location) {
        double distance = _location.distance(location);
        return distance <= DETECTION_RANGE;
    }

    @Override
    public String toString() {
        return "SignedLocationReport{" +
                "userId='" + _userId + '\'' +
                ", location='" + _location + '\'' +
                ", proofs=" + _proofs +
                '}';
    }

}
