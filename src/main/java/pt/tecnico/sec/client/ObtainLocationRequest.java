package pt.tecnico.sec.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

@SuppressWarnings("unused")
public class ObtainLocationRequest {
    private int _userId;
    private int _epoch;

    public ObtainLocationRequest() {}

    public ObtainLocationRequest(int _userId, int _epoch) {
        this._userId = _userId;
        this._epoch = _epoch;
    }

    // convert from bytes
    public static ObtainLocationRequest getFromBytes(byte[] requestBytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(requestBytes, ObtainLocationRequest.class);
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

    @Override
    public String toString() {
        return "ObtainLocationRequest{" +
                "_userId=" + _userId +
                ", _epoch=" + _epoch +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObtainLocationRequest that = (ObtainLocationRequest) o;
        return _userId == that._userId && _epoch == that._epoch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_userId, _epoch);
    }
}
