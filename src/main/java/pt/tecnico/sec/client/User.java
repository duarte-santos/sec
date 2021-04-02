package pt.tecnico.sec.client;

import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {
    private final RestTemplate _restTemplate;
    private final Grid _grid;
    private int _id;
    private Location _location;
    private final Map<Integer, List<LocationProof>> _proofs =  new HashMap<>();

    public User(RestTemplate restTemplate) {
        _restTemplate = restTemplate;
        _grid = Grid.getInstance();
        _grid.join(this);
    }

    /* ========================================================== */
    /* ====[               Getter and Setters               ]==== */
    /* ========================================================== */

    public Grid getGrid() {
        return _grid;
    }

    public int getId() {
        return _id;
    }

    public Location getLocation() {
        return _location;
    }

    public void setId(int id) {
        _id = id;
    }

    public void setLocation(int x, int y) throws IllegalArgumentException {
        if (!(0 <= x && x < _grid.getNX() && 0 <= y && y < _grid.getNY()))
            throw new IllegalArgumentException("x and y must be positive and inside the grid range.");
        _location = new Location(x, y);
    }


    /* ========================================================== */
    /* ====[             Request Location Proof             ]==== */
    /* ========================================================== */

    private LocationProof requestLocationProof(Location location, User user) {
        //FIXME URL port (and server?)
        //TODO how to send user/location?
        return _restTemplate.getForObject("http://localhost:8080/location-proof", LocationProof.class);
    }

    public void proveLocation() {
        List<User> nearbyUsers = _grid.findNearbyUsers(this);
        List<LocationProof> epochProofs = new ArrayList<>();
        int epoch = _grid.getEpoch();

        for (User user : nearbyUsers)
            epochProofs.add( requestLocationProof(_location, user) );

        _proofs.put(epoch, epochProofs);
    }


    /* ========================================================== */
    /* ====[             Submit Location Report             ]==== */
    /* ========================================================== */

    private void submitLocationReport(LocationReport locationReport) {
        //FIXME URL port (and server?) of server
        HttpEntity<LocationReport> request = new HttpEntity<>(locationReport);
        _restTemplate.postForObject("http://localhost:8080/location-report", request, LocationReport.class);
    }

    public void reportLocation(int epoch) {
        if (!(0 <= epoch && epoch <= _grid.getEpoch()))
            throw new IllegalArgumentException("Epoch must be positive and not exceed the current epoch.");

        List<LocationProof> epochProofs = _proofs.get(epoch);
        LocationReport locationReport = new LocationReport(_id, _location, epochProofs);

        submitLocationReport(locationReport);
    }

    /* ========================================================== */
    /* ====[             Obtain Location Report             ]==== */
    /* ========================================================== */

    private LocationReport obtainLocationReport(int epoch) {
        //FIXME URL port (and server?)
        //TODO how to send epoch?
        return _restTemplate.getForObject("http://localhost:8080/location-proof", LocationReport.class);
    }

    public void obtainReport(int epoch) {
        if (!(0 <= epoch && epoch <= _grid.getEpoch()))
            throw new IllegalArgumentException("Epoch must be positive and not exceed the current epoch.");
        LocationReport locationReport = obtainLocationReport(_grid.getEpoch());

        //TODO save on the map? why is this even necessary?
    }
}
