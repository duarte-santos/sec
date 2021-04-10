package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.server.DBValue;

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

    // convert from client version
    public Value(DBValue dbValue) {
        _location = new Location( dbValue.get_location() );
        _proverId = dbValue.get_proverId();
        _witnessId = dbValue.get_witnessId();
    }

    public Location get_location() {
        return _location;
    }

    public void set_location(Location _location) {
        this._location = _location;
    }

    public int get_proverId() {
        return _proverId;
    }

    public void set_proverId(int _proverId) {
        this._proverId = _proverId;
    }

    public int get_witnessId() {
        return _witnessId;
    }

    public void set_witnessId(int _witnessId) {
        this._witnessId = _witnessId;
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