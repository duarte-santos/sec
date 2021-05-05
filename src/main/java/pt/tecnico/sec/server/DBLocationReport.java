package pt.tecnico.sec.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.client.LocationProof;
import pt.tecnico.sec.client.LocationReport;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
public class DBLocationReport {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Integer id;

    private int _userId;

    private int _epoch;

    @ManyToOne(cascade=CascadeType.ALL)
    private DBLocation _DB_location;

    @OneToMany(cascade=CascadeType.ALL)
    private List<DBLocationProof> _DB_proofs = new ArrayList<>();

    @Column(length = 3000)
    private String _signature;

    public DBLocationReport() {}

    public DBLocationReport(int userId, int epoch, DBLocation DBLocation, List<DBLocationProof> proofs, String signature) {
        _userId = userId;
        _epoch = epoch;
        _DB_location = DBLocation;
        _DB_proofs = proofs;
        _signature = signature;
    }

    // convert from client version
    public DBLocationReport(LocationReport locationReport, String signature) {
        _userId = locationReport.get_userId();
        _epoch = locationReport.get_epoch();
        _DB_location = new DBLocation( locationReport.get_location() );
        for (LocationProof signedProof : locationReport.get_proofs())
            _DB_proofs.add(new DBLocationProof( signedProof ));
        _signature = signature;
    }

    @Override
    public String toString() {
        return "DBLocationReport{" +
                "id=" + id +
                ", _userId=" + _userId +
                ", _epoch=" + _epoch +
                ", _DB_location=" + _DB_location +
                ", _DB_proofs=" + _DB_proofs +
                ", _signature='" + _signature + '\'' +
                '}';
    }

    public int get_userId() {
        return _userId;
    }

    public void set_userId(int _userId) {
        this._userId = _userId;
    }

    public DBLocation get_location() {
        return _DB_location;
    }

    public void set_location(DBLocation _DB_location) {
        this._DB_location = _DB_location;
    }

    public List<DBLocationProof> get_DB_proofs() {
        return _DB_proofs;
    }

    public void set_DB_proofs(List<DBLocationProof> _proofs) {
        this._DB_proofs = _proofs;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int get_epoch() {
        return _epoch;
    }

    public void set_epoch(int _epoch) {
        this._epoch = _epoch;
    }

    public String get_signature() {
        return _signature;
    }

    public void set_signature(String _signature) {
        this._signature = _signature;
    }
}