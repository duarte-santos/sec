package pt.tecnico.sec.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.persistence.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
public class Value {

    @OneToOne(cascade=CascadeType.ALL)
    private Location _location;

    private int _proverId;
    private int _witnessId;
    //private int _epoch

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Integer id;

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

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}