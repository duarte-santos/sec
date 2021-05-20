package pt.tecnico.sec.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.client.LocationProof;

import javax.persistence.*;
import java.util.Objects;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
public class DBLocationProof {

    @OneToOne(cascade= CascadeType.ALL)
    private DBProofData _proofData;

    @Column(length = 3000)
    private String _signature = null;

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Integer id;

    public DBLocationProof() {}

    public DBLocationProof(DBProofData proofData, String signature) {
        _proofData = proofData;
        _signature = signature;
    }

    // convert from client version
    public DBLocationProof(LocationProof locationProof) {
        _signature = locationProof.get_signature();
        _proofData = new DBProofData( locationProof.get_proofData() );
    }

    public DBProofData get_proofData() {
        return _proofData;
    }

    public void set_proofData(DBProofData _proofData) {
        this._proofData = _proofData;
    }

    public String get_signature() {
        return _signature;
    }

    public void set_signature(String _signature) {
        this._signature = _signature;
    }

    public int get_witnessId() {
        return _proofData.get_witnessId();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String completeString() {
        return "DBLocationProof{" +
                "DBProofData='" + _proofData + '\'' +
                ", signature='" + _signature + '\'' +
                '}';
    }

    @Override
    public String toString() {
        return "DBLocationProof{" +
                "DBProofData='" + _proofData + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DBLocationProof that = (DBLocationProof) o;
        return Objects.equals(_proofData, that._proofData) && Objects.equals(_signature, that._signature) && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_proofData, _signature, id);
    }
}
