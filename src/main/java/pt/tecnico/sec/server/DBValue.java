package pt.tecnico.sec.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.client.Value;

import javax.persistence.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
public class DBValue {

    @OneToOne(cascade=CascadeType.ALL)
    private DBLocation _DB_location;

    private int _proverId;
    private int _witnessId;
    //private int _epoch

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Integer id;

    public DBValue() {}

    public DBValue(DBLocation DBLocation, int proverId, int witnessId) {
        _DB_location = DBLocation;
        _proverId = proverId;
        _witnessId = witnessId;
    }

    // convert from client version
    public DBValue(Value value) {
        _DB_location = new DBLocation( value.get_location() );
        _proverId = value.get_proverId();
        _witnessId = value.get_witnessId();
    }

    @Override
    public String toString() {
        return "DBValue{" +
                "_DB_location=" + _DB_location +
                ", _proverId=" + _proverId +
                ", _witnessId=" + _witnessId +
                ", id=" + id +
                '}';
    }

    public DBLocation get_location() {
        return _DB_location;
    }

    public void set_location(DBLocation _DB_location) {
        this._DB_location = _DB_location;
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