package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SignedLocationProof {
    private LocationProof _locationProof;
    private String _signature;

    public SignedLocationProof() {}

    public SignedLocationProof(LocationProof locationProof, String signature) {
        _locationProof = locationProof;
        _signature = signature;
    }

    public LocationProof get_locationProof() {
        return _locationProof;
    }

    public void set_locationProof(LocationProof _locationProof) {
        this._locationProof = _locationProof;
    }

    public String get_signature() {
        return _signature;
    }

    public void set_signature(String _signature) {
        this._signature = _signature;
    }

    public int get_witnessId() {
        return _locationProof.get_witnessId();
    }

    public String completeToString() {
        return "SignedLocationProof{" +
                "locationProof='" + _locationProof + '\'' +
                ", signature=" + _signature +
                '}';
    }

    @Override
    public String toString() {
        return "SignedLocationProof{" +
                "locationProof='" + _locationProof +
                '}';
    }
}
