package pt.tecnico.sec.healthauthority;

import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.sec.client.Location;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class UsersAtLocation {
    private Location _location;
    private int _epoch;
    private List<Integer> _userIds = new ArrayList<>();

    public UsersAtLocation() {}

    public UsersAtLocation(Location _location, int _epoch, List<Integer> _userIds) {
        this._location = _location;
        this._epoch = _epoch;
        this._userIds = _userIds;
    }

    // convert from bytes
    public static UsersAtLocation getFromBytes(byte[] userListBytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(userListBytes, UsersAtLocation.class);
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

    public List<Integer> get_userIds() {
        return _userIds;
    }

    public void set_userIds(List<Integer> _userIds) {
        this._userIds = _userIds;
    }

    @Override
    public String toString() {
        return "UsersAtLocation{" +
                "_location=" + _location +
                ", _epoch=" + _epoch +
                ", _userIds=" + _userIds +
                '}';
    }
}
