package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.server.DBLocationProof;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationProof {

    private String _type;
    private Value _value;

    public LocationProof() {}

    public LocationProof(String type, Value value) {
        _type = type;
        _value = value;
    }

    // convert from server version
    public LocationProof(DBLocationProof dbLocationProof) {
        _type = dbLocationProof.get_type();
        _value = new Value( dbLocationProof.get_value() );
    }

    public String get_type() {
        return _type;
    }

    public void set_type(String _type) {
        this._type = _type;
    }

    public Value get_value() {
        return _value;
    }

    public void set_value(Value _value) {
        this._value = _value;
    }

    public int get_witnessId() {
        return _value.get_witnessId();
    }

    @Override
    public String toString() {
        return "LocationProof{" +
                "type='" + _type + '\'' +
                ", value=" + _value +
                '}';
    }

}
