package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Value {

    private Location _location;
    private int _proverId;
    private int _witnessId;
    //private int _epoch

    public Value() {}

    public Value(Location location, int proverId, int witnessId) {
        _location = location;
        _proverId = proverId;
        _witnessId = witnessId;
    }

    public Location getLocation() {
        return _location;
    }

    public void setLocation(Location location) {
        _location = location;
    }

    public int getProverId() {
        return _proverId;
    }

    public void setProverId(int proverId) {
        _proverId = proverId;
    }

    public int getWitnessId() {
        return _witnessId;
    }

    public void setWitnessId(int witnessId) {
        _witnessId = witnessId;
    }

    @Override
    public String toString() {
        return "Value{" +
                "location='" + _location + '\'' +
                ", proverId='" + _proverId + '\'' +
                ", witnessId=" + _witnessId +
                '}';
    }
}