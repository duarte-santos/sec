package pt.tecnico.sec.healthauthority;

import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.sec.RSAKeyGenerator;

import java.security.PrivateKey;
import java.util.Arrays;

public class SecureObtainUsersRequest {
    private ObtainUsersRequest _request;
    private byte[] _signature;

    public SecureObtainUsersRequest() {}

    public SecureObtainUsersRequest(ObtainUsersRequest _request, byte[] _signature) {
        this._request = _request;
        this._signature = _signature;
    }

    public SecureObtainUsersRequest(ObtainUsersRequest request, PrivateKey signKey) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] reportBytes = objectMapper.writeValueAsBytes(request);
        _request = request;
        _signature = RSAKeyGenerator.sign(reportBytes, signKey);
    }

    public ObtainUsersRequest get_request() {
        return _request;
    }

    public void set_request(ObtainUsersRequest _request) {
        this._request = _request;
    }

    public byte[] get_signature() {
        return _signature;
    }

    public void set_signature(byte[] _signature) {
        this._signature = _signature;
    }


    @Override
    public String toString() {
        return "SecureObtainUsersRequest{" +
                "_request=" + _request +
                ", _signature=" + Arrays.toString(_signature) +
                '}';
    }
}
