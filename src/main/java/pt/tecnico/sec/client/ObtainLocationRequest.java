package pt.tecnico.sec.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.sec.RSAKeyGenerator;

import java.security.PublicKey;

public class ObtainLocationRequest {
    private int _userId;
    private int _epoch;

    public ObtainLocationRequest() {}

    public ObtainLocationRequest(int _userId, int _epoch) {
        this._userId = _userId;
        this._epoch = _epoch;
    }

    public int get_userId() {
        return _userId;
    }

    public void set_userId(int _userId) {
        this._userId = _userId;
    }

    public int get_epoch() {
        return _epoch;
    }

    public void set_epoch(int _epoch) {
        this._epoch = _epoch;
    }

    public void verify(byte[] signature, PublicKey verifyKey) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] requestBytes = objectMapper.writeValueAsBytes(this);

        // Verify signature
        if (signature == null || !RSAKeyGenerator.verify(requestBytes, signature, verifyKey)) {
            throw new IllegalArgumentException("Request signature failed!"); //FIXME type of exception
        }
    }

    @Override
    public String toString() {
        return "ObtainLocationRequest{" +
                "_userId=" + _userId +
                ", _epoch=" + _epoch +
                '}';
    }
}
