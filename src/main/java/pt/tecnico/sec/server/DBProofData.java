package pt.tecnico.sec.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.client.ProofData;

import javax.persistence.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
public class DBProofData {

    @OneToOne(cascade=CascadeType.ALL)
    private DBLocation _DB_location;

    private int _proverId;
    private int _witnessId;
    private int _epoch;
    private String _type;

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Integer id;

    public DBProofData() {}

    public DBProofData(DBLocation DBLocation, int proverId, int witnessId, int epoch, String type) {
        _DB_location = DBLocation;
        _proverId = proverId;
        _witnessId = witnessId;
        _epoch = epoch;
        _type = type;
    }

    // convert from client version
    public DBProofData(ProofData proofData) {
        _DB_location = new DBLocation( proofData.get_location() );
        _proverId = proofData.get_proverId();
        _witnessId = proofData.get_witnessId();
        _epoch = proofData.get_epoch();
        _type = proofData.get_type();
    }

    public DBLocation get_DB_location() {
        return _DB_location;
    }

    public void set_DB_location(DBLocation _DB_location) {
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

    public int get_epoch() {
        return _epoch;
    }

    public void set_epoch(int _epoch) {
        this._epoch = _epoch;
    }

    public String get_type() {
        return _type;
    }

    public void set_type(String _type) {
        this._type = _type;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "DBProofData{" +
                "_DB_location=" + _DB_location +
                ", _proverId=" + _proverId +
                ", _witnessId=" + _witnessId +
                ", _epoch=" + _epoch +
                ", _type='" + _type + '\'' +
                ", id=" + id +
                '}';
    }
}