package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.sec.ObjectMapperHandler;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.server.DBLocationProof;
import pt.tecnico.sec.server.DBLocationReport;
import pt.tecnico.sec.server.exception.ReportNotAcceptableException;

import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static pt.tecnico.sec.Constants.DETECTION_RANGE;
import static pt.tecnico.sec.Constants.SUCCESS;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationReport {

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
        for (DBLocationProof dbProof : dbLocationReport.get_DB_proofs())
            _proofs.add(new LocationProof( dbProof ));
    }

    // convert from bytes
    public static LocationReport getFromBytes(byte[] reportBytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(reportBytes, LocationReport.class);
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

    public LocationProof get_witness_proof(int id) {
        for (LocationProof proof : _proofs)
            if (proof.get_witnessId() == id) return proof;
        return null;
    }

    public void checkSender(int sender_id) {
        if (sender_id != _userId)
            throw new ReportNotAcceptableException("Cannot submit reports from other users");
    }

    @Override
    public String toString() {
        return "LocationReport{" +
                "_userId=" + _userId +
                ", _epoch=" + _epoch +
                ", _location=" + _location +
                ", _proofs=" + getValidProofs() +
                '}';
    }

    /* ========================================================== */
    /* ====[                Proof validation                ]==== */
    /* ========================================================== */

    private boolean isNearby(Location location) {
        double distance = _location.distance(location);
        return distance <= DETECTION_RANGE;
    }

    public boolean isProofValid(LocationProof signedProof, Set<Integer> prevWitnessIds) throws Exception {
        ProofData proofData = signedProof.get_proofData();
        byte[] data = ObjectMapperHandler.writeValueAsBytes(proofData);
        String signature = signedProof.get_signature();
        PublicKey clientKey = RSAKeyGenerator.readClientPublicKey(signedProof.get_witnessId());

        return !( signature == null || !RSAKeyGenerator.verify(data, signature, clientKey)
                || proofData.get_epoch() != _epoch
                || !isNearby(proofData.get_location())
                || !proofData.get_type().equals(SUCCESS)
                || proofData.get_proverId() != _userId
                || prevWitnessIds.contains(proofData.get_witnessId())
                || proofData.get_witnessId() == _userId);
    }

    public int verifyProofs() throws Exception {
        Set<Integer> prevWitnessIds = new HashSet<>();

        for (LocationProof signedProof : _proofs) {
            if ( isProofValid(signedProof, prevWitnessIds) )
                prevWitnessIds.add( signedProof.get_witnessId() ); // keep track of witnesses, can't be repeated
            else
                System.out.println("Invalid LocationProof: " + signedProof);
        }
        return prevWitnessIds.size(); // valid proof count
    }

    public List<LocationProof> getValidProofs() {
        Set<Integer> prevWitnessIds = new HashSet<>();
        List<LocationProof> validProofs = new ArrayList<>();

        for (LocationProof signedProof : _proofs) {
            try {
                if (isProofValid(signedProof, prevWitnessIds)) {
                    prevWitnessIds.add(signedProof.get_witnessId());
                    validProofs.add(signedProof);
                }
            } catch (Exception ignored) {
                // function is used to print, ignore exception
            }
        }
        return validProofs;
    }
}
