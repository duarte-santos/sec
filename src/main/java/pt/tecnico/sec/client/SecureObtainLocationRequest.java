package pt.tecnico.sec.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.sec.RSAKeyGenerator;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

public class SecureObtainLocationRequest {
    private ObtainLocationRequest _request;
    private byte[] _signature;

    public SecureObtainLocationRequest() {}

    public SecureObtainLocationRequest(ObtainLocationRequest _request, byte[] _signature) {
        this._request = _request;
        this._signature = _signature;
    }

    public SecureObtainLocationRequest(ObtainLocationRequest request, PrivateKey signKey) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] reportBytes = objectMapper.writeValueAsBytes(request);
        _request = request;
        _signature = RSAKeyGenerator.sign(reportBytes, signKey);
    }

    public ObtainLocationRequest get_request() {
        return _request;
    }

    public void set_request(ObtainLocationRequest _request) {
        this._request = _request;
    }

    public byte[] get_signature() {
        return _signature;
    }

    public void set_signature(byte[] _signature) {
        this._signature = _signature;
    }

    public void verify(PublicKey verifyKey) throws Exception {
        _request.verify(_signature, verifyKey);
    }

    @Override
    public String toString() {
        return "SecureObtainLocationRequest{" +
                "_request=" + _request +
                ", _signature=" + Arrays.toString(_signature) +
                '}';
    }
}
