package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationProof {

    private String _type;
    private Value _value;

    public LocationProof() {}

    public LocationProof(String type, Value value) {
        _type = type;
        _value = value;
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

    @Override
    public String toString() {
        return "LocationProof{" +
                "type='" + _type + '\'' +
                ", value=" + _value +
                '}';
    }

}
