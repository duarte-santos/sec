package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.server.DBProofData;

import java.util.Arrays;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationProof {
    private ProofData _proofData;
    private byte[] _signature = null;

    public LocationProof() {}

    public LocationProof(ProofData proofData, byte[] signature) {
        _proofData = proofData;
        _signature = signature;
    }

    public LocationProof(DBProofData dbProofData) {
        _proofData = new ProofData(dbProofData);
    }

    public ProofData get_proofData() {
        return _proofData;
    }

    public void set_proofData(ProofData _proofData) {
        this._proofData = _proofData;
    }

    public byte[] get_signature() {
        return _signature;
    }

    public void set_signature(byte[] _signature) {
        this._signature = _signature;
    }

    public int get_witnessId() {
        return _proofData.get_witnessId();
    }

    public String completeString() {
        return "LocationProof{" +
                "proofData='" + _proofData + '\'' +
                ", signature=" + Arrays.toString(_signature) +
                '}';
    }

    @Override
    public String toString() {
        return "LocationProof{" +
                "proofData='" + _proofData + '\'' +
                '}';
    }
}
