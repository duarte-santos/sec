package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.server.DBLocationProof;

import java.security.PublicKey;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationProof {
    private ProofData _proofData;
    private String _signature = null;

    public LocationProof() {}

    public LocationProof(ProofData proofData, String signature) {
        _proofData = proofData;
        _signature = signature;
    }

    // convert from server version
    public LocationProof(DBLocationProof dbLocationProof) {
        _signature = dbLocationProof.get_signature();
        _proofData = new ProofData( dbLocationProof.get_proofData() );
    }

    public ProofData get_proofData() {
        return _proofData;
    }

    public void set_proofData(ProofData _proofData) {
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

    public int get_epoch() {
        return _proofData.get_epoch();
    }

    public void verify(PublicKey verifyKey) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes = objectMapper.writeValueAsBytes(_proofData);
        if (_signature == null || !RSAKeyGenerator.verify(bytes, _signature, verifyKey))
            throw new IllegalArgumentException("Signature verify failed!");
    }

    public String completeString() {
        return "LocationProof{" +
                "proofData='" + _proofData + '\'' +
                ", signature='" + _signature + '\'' +
                '}';
    }

    @Override
    public String toString() {
        return "LocationProof{" +
                "proofData='" + _proofData + '\'' +
                '}';
    }
}
