package pt.tecnico.sec.client;

import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {
    private static final int BASE_PORT = 8000;
    private static final int SERVER_PORT = 9000;

    private RestTemplate _restTemplate;
    private final Environment _environment;
    private final int _id;
    private final Map<Integer, List<LocationProof>> _proofs =  new HashMap<>();

    // used for easy access
    private Grid _grid;
    private Location _location;

    private int _epoch = 0;

    public User(Environment environment, int id) {
        _environment = environment;
        _id = id;

        updateMe();
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        _restTemplate = restTemplate;
    }

    public int getId() {
        return _id;
    }

    public Location getLocation() {
        return _location;
    }

    public Grid getGrid() {
        return _grid;
    }

    public int getEpoch() {
        return _epoch;
    }

    /* ========================================================== */
    /* ====[                   Auxiliary                    ]==== */
    /* ========================================================== */

    public String getUserURL(int userId) {
        int port = BASE_PORT + userId;
        return "http://localhost:" + port;
    }

    public String getServerURL() {
        return "http://localhost:" + SERVER_PORT;
    }


    /* ========================================================== */
    /* ====[                      Step                      ]==== */
    /* ========================================================== */

    public void step() {
        // TODO : the visitor design pattern might be useful in the future
        // TODO : warn other users to make a step?

        if (_epoch >= _environment.getMaxEpoch()) // check if epoch is covered by environment
            throw new IllegalArgumentException("No more steps available in environment.");

        proveLocation();

        System.out.println("Leaving epoch " + _epoch + " and location " + _location + ". Bye!!");
        _epoch++;
        updateMe();
        System.out.println("New epoch " + _epoch + " and location " + _location + ". So fresh!\n");
    }

    public void updateMe() {
        _grid = _environment.getGrid(_epoch);
        _location = _grid.getUserLocation(_id);

    }

    /* ========================================================== */
    /* ====[             Request Location Proof             ]==== */
    /* ========================================================== */

    private LocationProof requestLocationProof(int userId) {
        //FIXME URL server?
        Map<String, Integer> params = new HashMap<>();
        params.put("proverId", _id);
        System.out.println("Sending location proof request to url " + getUserURL(userId) + "/location-proof/" + _id);
        return _restTemplate.getForObject(getUserURL(userId)+ "/location-proof/{proverId}", LocationProof.class, params);
    }

    public void proveLocation() {
        List<Integer> nearbyUsers = _grid.findNearbyUsers(_id);
        List<LocationProof> epochProofs = new ArrayList<>();

        for (int userId : nearbyUsers) {
            System.out.println("Found nearby user " + userId + " (epoch " + _epoch + ")");
            epochProofs.add(requestLocationProof(userId));
        }

        _proofs.put(_epoch, epochProofs);
    }


    /* ========================================================== */
    /* ====[             Submit Location Report             ]==== */
    /* ========================================================== */

    private void submitLocationReport(LocationReport locationReport) {
        //FIXME URL server and port?
        HttpEntity<LocationReport> request = new HttpEntity<>(locationReport);
        _restTemplate.postForObject(getServerURL() + "/location-report", request, LocationReport.class);
    }

    public void reportLocation(int epoch) {
        if (!(0 <= epoch && epoch <= _epoch))
            throw new IllegalArgumentException("Epoch must be positive and not exceed the current epoch.");

        Location epochLocation = _environment.getGrid(epoch).getUserLocation(_id);
        List<LocationProof> epochProofs = _proofs.get(epoch);
        LocationReport locationReport = new LocationReport(_id, epochLocation, epochProofs);

        submitLocationReport(locationReport);
    }

    /* ========================================================== */
    /* ====[             Obtain Location Report             ]==== */
    /* ========================================================== */

    private LocationReport obtainLocationReport(int epoch) {
        //FIXME URL server?
        //TODO how to send epoch?
        return _restTemplate.getForObject(getServerURL() + "/location-report", LocationReport.class);
    }

    public void obtainReport(int epoch) {
        if (!(0 <= epoch && epoch <= _epoch))
            throw new IllegalArgumentException("Epoch must be positive and not exceed the current epoch.");
        LocationReport locationReport = obtainLocationReport(epoch);

        //TODO save on the map? why is this even necessary?
    }
}
