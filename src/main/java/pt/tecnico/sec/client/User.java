package pt.tecnico.sec.client;

import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {
    private static final int BASE_PORT = 8000;
    private static final int SERVER_PORT = 8001;

    private RestTemplate _restTemplate;
    private final Environment _environment;
    private final int _id;
    private final Map<Integer, List<LocationProof>> _proofs =  new HashMap<>();

    private int _epoch = 0;

    public User(Environment environment, int id) {
        _environment = environment;
        _id = id;
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        _restTemplate = restTemplate;
    }

    public int getId() {
        return _id;
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

    public List<Integer> getUserList() {
        return _environment.getUserList();
    }

    public boolean isNearby(int epoch, int userId) {
        return _environment.getGrid(epoch).isNearby(_id, userId);
    }

    public Location getEpochLocation(int epoch) {
        return _environment.getGrid(epoch).getUserLocation(_id);
    }

    public List<Integer> findNearbyUsers() {
        return _environment.getGrid(_epoch).findNearbyUsers(_id);
    }


    /* ========================================================== */
    /* ====[                      Step                      ]==== */
    /* ========================================================== */

    public void step() {
        // TODO : the visitor design pattern might be useful in the future
        proveLocation();

        if (_epoch >= _environment.getMaxEpoch()) // check if epoch is covered by environment
            throw new IllegalArgumentException("No more steps available in environment.");

        System.out.println("Leaving: epoch " + _epoch + ", location " + getEpochLocation(_epoch) + ". Bye!!");
        _epoch++;
        System.out.println("Current: epoch " + _epoch + ", location " + getEpochLocation(_epoch) + ". So fresh!");
    }

    private void stepRequest(int userId) {
        System.out.println("[Request sent] Type: Step To: " + getUserURL(userId) + ", From: " + _id);
        _restTemplate.getForObject(getUserURL(userId)+ "/step/", LocationProof.class);
    }

    public void globalStep() {
        step();

        List<Integer> userList = getUserList();
        for (int userId : userList) {
            if (userId == _id) continue; // do not send request to myself
            stepRequest(userId);
        }
    }


    /* ========================================================== */
    /* ====[             Request Location Proof             ]==== */
    /* ========================================================== */

    private LocationProof requestLocationProof(int userId) {
        //FIXME URL server?
        Map<String, Integer> params = new HashMap<>();
        params.put("epoch", _epoch);
        params.put("proverId", _id);
        System.out.println("[Request sent] Type: LocationProof To: " + getUserURL(userId) + ", From: " + _id + ", Epoch: " + _epoch);
        return _restTemplate.getForObject(getUserURL(userId)+ "/location-proof/{epoch}/{proverId}", LocationProof.class, params);
    }

    public void proveLocation() {
        List<Integer> nearbyUsers = findNearbyUsers();
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
        System.out.println(request);
        _restTemplate.postForEntity(getServerURL() + "/location-report", request, LocationReport.class);
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
