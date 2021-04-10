package pt.tecnico.sec.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.client.LocationProof;

import javax.persistence.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
public class DBLocationProof {

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Integer id;

    private String _type;

    @OneToOne(cascade=CascadeType.ALL)
    private DBValue _DB_value;

    public DBLocationProof() {}

    public DBLocationProof(String type, DBValue DBValue) {
        _type = type;
        _DB_value = DBValue;
    }

    // convert from client version
    public DBLocationProof(LocationProof locationProof) {
        _type = locationProof.get_type();
        _DB_value = new DBValue( locationProof.get_value() );
    }

    @Override
    public String toString() {
        return "DBLocationProof{" +
                "id=" + id +
                ", _type='" + _type + '\'' +
                ", _DB_value=" + _DB_value +
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

    public DBValue get_value() {
        return _DB_value;
    }

    public void set_value(DBValue _DB_value) {
        this._DB_value = _DB_value;
    }

    public int get_witnessId() {
        return _DB_value.get_witnessId();
    }
}
