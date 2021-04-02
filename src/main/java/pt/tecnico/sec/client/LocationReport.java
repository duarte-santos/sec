package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationReport {

    private int _userId;
    private Location _location;
    private List<LocationProof> _proofs = new ArrayList<>();

    public LocationReport() {}

    public LocationReport(int userId, Location location, List<LocationProof> proofs) {
        _userId = userId;
        _location = location;
        _proofs = proofs;
    }

    public int getUserId() {
        return _userId;
    }

    public void setUserId(int userId) {
        _userId = userId;
    }

    public Location getLocation() {
        return _location;
    }

    public void setLocation(Location location) {
        _location = location;
    }

    public List<LocationProof> getProofs() {
        return _proofs;
    }

    public void setProofs(List<LocationProof> proofs) {
        _proofs = proofs;
    }

    @Override
    public String toString() {
        return "LocationReport{" +
                "userId='" + _userId + '\'' +
                ", location='" + _location + '\'' +
                ", proofs=" + _proofs +
                '}';
    }

}
