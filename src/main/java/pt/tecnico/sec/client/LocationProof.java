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

    @Override
    public String toString() {
        return "LocationProof{" +
                "type='" + _type + '\'' +
                ", value=" + _value +
                '}';
    }

}
