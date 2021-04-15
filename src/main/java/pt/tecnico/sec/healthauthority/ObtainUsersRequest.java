package pt.tecnico.sec.healthauthority;

import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.sec.client.Location;

import java.io.IOException;

@SuppressWarnings("unused")
public class ObtainUsersRequest {
    private Location _location;
    private int _epoch;

    public ObtainUsersRequest() {}

    public ObtainUsersRequest(Location _location, int _epoch) {
        this._location = _location;
        this._epoch = _epoch;
    }

    public ObtainUsersRequest(int x, int y, int _epoch) {
        this._location = new Location(x, y);
        this._epoch = _epoch;
    }

    public Location get_location() {
        return _location;
    }

    public void set_location(Location _location) {
        this._location = _location;
    }

    public int get_epoch() {
        return _epoch;
    }

    public void set_epoch(int _epoch) {
        this._epoch = _epoch;
    }


    // convert from bytes
    public static ObtainUsersRequest getFromBytes(byte[] requestBytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(requestBytes, ObtainUsersRequest.class);
    }

    @Override
    public String toString() {
        return "ObtainUsersRequest{" +
                "_location=" + _location +
                ", _epoch=" + _epoch +
                '}';
    }
}
