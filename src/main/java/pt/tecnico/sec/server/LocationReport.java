package pt.tecnico.sec.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
public class LocationReport {

    private int _userId;

    @ManyToOne
    private Location _location;

    @OneToMany
    private List<LocationProof> _proofs = new ArrayList<>();

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Integer id;

    public LocationReport() {}

    public LocationReport(int userId, Location location, List<LocationProof> proofs) {
        _userId = userId;
        _location = location;
        _proofs = proofs;
    }

    @Override
    public String toString() {
        return "LocationReport{" +
                "userId='" + _userId + '\'' +
                ", location='" + _location + '\'' +
                ", proofs=" + _proofs +
                '}';
    }

    public int get_userId() {
        return _userId;
    }

    public void set_userId(int _userId) {
        this._userId = _userId;
    }

    public Location get_location() {
        return _location;
    }

    public void set_location(Location _location) {
        this._location = _location;
    }

    public List<LocationProof> get_proofs() {
        return _proofs;
    }

    public void set_proofs(List<LocationProof> _proofs) {
        this._proofs = _proofs;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}
