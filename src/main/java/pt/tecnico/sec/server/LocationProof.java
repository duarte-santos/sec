package pt.tecnico.sec.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.persistence.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
public class LocationProof {

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Integer id;

    private String _type;

    @OneToOne(cascade=CascadeType.ALL)
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

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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
}
