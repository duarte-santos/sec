package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.server.DBLocationReport;
import pt.tecnico.sec.server.DBProofData;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationReport {

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

    @Override
    public String toString() {
        return "SignedLocationReport{" +
                "userId='" + _userId + '\'' +
                ", location='" + _location + '\'' +
                ", proofs=" + _proofs +
                '}';
    }

}
