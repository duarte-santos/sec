package pt.tecnico.sec.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.RSAKeyGenerator;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {
    private static final int BASE_PORT = 8000;
    private static final int SERVER_PORT = 9000;

    private RestTemplate _restTemplate;
    private Grid _prevGrid = null; // useful for synchronization
    private Grid _grid;
    private int _epoch = 0;

    private final int _id;
    private final Map<Integer, List<LocationProof>> _proofs =  new HashMap<>();

    private final KeyPair _keyPair;
    private final PublicKey _serverKey;

    public User(Grid grid, int id, KeyPair keyPair, PublicKey serverKey) {
        _grid = grid;
        _id = id;
        _keyPair = keyPair;
        _serverKey = serverKey;
    }


    /* ========================================================== */
    /* ====[               Getters and Setters              ]==== */
    /* ========================================================== */

    public void setRestTemplate(RestTemplate restTemplate) {
        _restTemplate = restTemplate;
    }

    public int getId() {
        return _id;
    }

    public int getEpoch() {
        return _epoch;
    }

    public Location getLocation() {
        return _grid.getUserLocation(_id);
    }

    public Location getPrevLocation() {
        return _prevGrid.getUserLocation(_id);
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

    public List<Integer> findNearbyUsers() {
        return _grid.findNearbyUsers(_id);
    }

    public boolean isNearby(int userId) {
        return _grid.isNearby(_id, userId);
    }

    public boolean wasNearby(int userId) {
        return _prevGrid.isNearby(_id, userId);
    }


    /* ========================================================== */
    /* ====[                      Step                      ]==== */
    /* ========================================================== */

    public void step(Grid nextGrid) {
        System.out.println("Previous epoch " + _epoch + ", location " + getLocation());
        proveLocation();
        _epoch++;
        _prevGrid = _grid;
        _grid = nextGrid;
        System.out.println("Current epoch " + _epoch + ", location " + getLocation());
    }

    public void stepRequest(int userId) {
        System.out.println("[Request sent] Type: Step To: " + getUserURL(userId) + ", From: " + _id);
        _restTemplate.getForObject(getUserURL(userId)+ "/step/", String.class); //FIXME
    }


    /* ========================================================== */
    /* ====[             Request Location Proof             ]==== */
    /* ========================================================== */

    private LocationProof requestLocationProof(int userId) {
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

    public List<LocationProof> getEpochProofs(int epoch) {
        List<LocationProof> epochProofs = _proofs.get(epoch);
        return (epochProofs != null) ? epochProofs : new ArrayList<>();
    }


    /* ========================================================== */
    /* ====[               Send Location Proof              ]==== */
    /* ========================================================== */

    private LocationProof signLocationProof(ProofData proofData) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] proofBytes = objectMapper.writeValueAsBytes(proofData);
        byte[] signature = RSAKeyGenerator.sign(proofBytes, _keyPair.getPrivate());
        return new LocationProof(proofData, signature);
    }

    public LocationProof makeLocationProof(int proverId, int proverEpoch) throws Exception {
        // check proximity
        Location witnessLoc;
        String type;
        if (proverEpoch == _epoch) { // users are synchronized
            witnessLoc = getLocation();
            type = isNearby(proverId) ? "success" : "failure";
        } else if (proverEpoch == _epoch - 1) { // prover is not synchronized yet
            witnessLoc = getPrevLocation();
            type = wasNearby(proverId) ? "success" : "failure";
        } else
            throw new IllegalArgumentException("Can only prove location requests regarding current or previous epoch");

        // build and sign proof
        ProofData proofData = new ProofData(witnessLoc, proverId, _id, proverEpoch, type);
        return signLocationProof(proofData);
    }


    /* ========================================================== */
    /* ====[             Submit Location Report             ]==== */
    /* ========================================================== */

    private SecureLocationReport secureLocationReport(LocationReport locationReport) throws Exception {
        // make secret key
        SecretKey secretKey = AESKeyGenerator.makeAESKey();

        // encrypt report with secret key
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] reportBytes = objectMapper.writeValueAsBytes(locationReport);
        byte[] cipheredReport = AESKeyGenerator.encrypt(reportBytes, secretKey);

        // encrypt secret key with server public key
        byte[] cipheredSecretKey = RSAKeyGenerator.encryptSecretKey(secretKey, _serverKey);

        // sign report with client private key
        byte[] signature = RSAKeyGenerator.sign(reportBytes, _keyPair.getPrivate());

        // build secure report
        return new SecureLocationReport(cipheredSecretKey, cipheredReport, signature);
    }

    private void submitLocationReport(SecureLocationReport secureLocationReport) {
        HttpEntity<SecureLocationReport> request = new HttpEntity<>(secureLocationReport);
        _restTemplate.postForObject(getServerURL() + "/location-report", request, SecureLocationReport.class);
    }

    public void reportLocation(int epoch, Location epochLocation) throws Exception {
        if (!(0 <= epoch && epoch <= _epoch))
            throw new IllegalArgumentException("Epoch must be positive and not exceed the current epoch.");

        List<LocationProof> epochProofs = getEpochProofs(epoch);
        LocationReport locationReport = new LocationReport(_id, epoch, epochLocation, epochProofs);
        System.out.println(locationReport);

        SecureLocationReport secureLocationReport = secureLocationReport(locationReport);
        submitLocationReport(secureLocationReport);
    }


    /* ========================================================== */
    /* ====[             Obtain Location Report             ]==== */
    /* ========================================================== */

    public LocationReport decipherReport(SecureLocationReport secureReport) throws Exception {
        // Decipher secret key
        byte[] cipheredKey = secureReport.get_cipheredKey();
        SecretKey secretKey = RSAKeyGenerator.decryptSecretKey(cipheredKey, _keyPair.getPrivate());

        // Decipher report
        byte[] data = AESKeyGenerator.decrypt(secureReport.get_cipheredReport(), secretKey);
        ObjectMapper objectMapper = new ObjectMapper();
        LocationReport report = objectMapper.readValue(data, LocationReport.class);

        // Verify signature
        byte[] signature = secureReport.get_signature();
        if (signature == null || !RSAKeyGenerator.verify(data, signature, _serverKey)) {
            throw new IllegalArgumentException("Report signature failed!"); //FIXME type of exception
        }

        return report;
    }

    private SecureLocationReport obtainLocationReport(int epoch) {
        Map<String, Integer> params = new HashMap<>();
        params.put("epoch", epoch);
        params.put("userId", _id);
        return _restTemplate.getForObject(getServerURL() + "/location-report/{epoch}/{userId}", SecureLocationReport.class, params);
    }

    public LocationReport obtainReport(int epoch) throws Exception {
        if (!(0 <= epoch && epoch <= _epoch))
            throw new IllegalArgumentException("Epoch must be positive and not exceed the current epoch.");

        SecureLocationReport secureLocationReport = obtainLocationReport(epoch);
        if (secureLocationReport == null) return null;

        // Decipher and check signature
        return decipherReport(secureLocationReport);
    }



}
