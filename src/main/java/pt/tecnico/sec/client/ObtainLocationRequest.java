package pt.tecnico.sec.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

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

    // convert from bytes
    public static ObtainLocationRequest getFromBytes(byte[] requestBytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(requestBytes, ObtainLocationRequest.class);
    }

    @Override
    public String toString() {
        return "ObtainLocationRequest{" +
                "_userId=" + _userId +
                ", _epoch=" + _epoch +
                '}';
    }
}