package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SignedLocationReport {

    private int _userId;
    private int _epoch;
    private Location _location;
    private List<SignedLocationProof> _proofs = new ArrayList<>();

    public SignedLocationReport() {}

    public SignedLocationReport(int userId, int epoch, Location location, List<SignedLocationProof> proofs) {
        _userId = userId;
        _epoch = epoch;
        _location = location;
        _proofs = proofs;
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

    public List<SignedLocationProof> get_proofs() {
        return _proofs;
    }

    public void set_proofs(List<SignedLocationProof> _proofs) {
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
