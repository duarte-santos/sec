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

    @Override
    public String toString() {
        return "Value{" +
                "location='" + _location + '\'' +
                ", proverId='" + _proverId + '\'' +
                ", witnessId=" + _witnessId +
                '}';
    }
}