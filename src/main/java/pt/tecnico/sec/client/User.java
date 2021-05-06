package pt.tecnico.sec.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.RSAKeyGenerator;
import pt.tecnico.sec.server.exception.ReportNotAcceptableException;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;

public class User {
    private static final int BASE_PORT = 8000;
    private static final int SERVER_BASE_PORT = 9000;
    private static final int BYZANTINE_USERS = 1;

    private RestTemplate _restTemplate;
    private Grid _prevGrid = null; // useful for synchronization
    private Grid _grid;
    private int _epoch = 0;

    private final int _id;
    private final Map<Integer, List<LocationProof>> _proofs =  new HashMap<>();

    private final KeyPair _keyPair;
    private final PublicKey[] _serverKeys;

    public User(Grid grid, int id, KeyPair keyPair, PublicKey[] serverKeys) {
        _grid = grid;
        _id = id;
        _keyPair = keyPair;
        _serverKeys = serverKeys;
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

    public Location getLocation() {
        return _grid.getUserLocation(_id);
    }

    public Location getPrevLocation() {
        return _prevGrid.getUserLocation(_id);
    }


    /* ========================================================== */
    /* ====[                   Auxiliary                    ]==== */
    /* ========================================================== */

    private SecureMessage postToServer(byte[] messageBytes, SecretKey secretKey, String endpoint) {
        SecureMessage[] secureMessages = new SecureMessage[_serverKeys.length];
        for (int serverId = 0; serverId < _serverKeys.length; serverId++) {
            PublicKey serverKey = _serverKeys[serverId];
            SecureMessage secureRequest = new SecureMessage(messageBytes, secretKey, serverKey, _keyPair.getPrivate());
            HttpEntity<SecureMessage> request = new HttpEntity<>(secureMessage);
            secureMessages[serverId] = _restTemplate.postForObject(getServerURL(serverId) + endpoint, request, SecureMessage.class);
        }
        return secureMessages[0];
    }

    public String getUserURL(int userId) {
        int port = BASE_PORT + userId;
        return "http://localhost:" + port;
    }

    public String getServerURL(int serverId) {
        int serverPort = SERVER_BASE_PORT + serverId;
        return "http://localhost:" + serverPort;
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

    public byte[] writeValueAsBytes(ProofData proofData) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(proofData);
    }

    public byte[] writeValueAsBytes(LocationReport report) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(report);
    }

    public byte[] writeValueAsBytes(ObtainLocationRequest request) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(request);
    }

    public byte[] writeValueAsBytes(WitnessProofsRequest request) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(request);
    }


    /* ========================================================== */
    /* ====[                      Step                      ]==== */
    /* ========================================================== */

    public void step(Grid nextGrid) {
        System.out.println("Stepping...");
        System.out.println("Previous epoch " + _epoch + ", location " + getLocation());
        proveLocation();
        _epoch++;
        _prevGrid = _grid;
        _grid = nextGrid;
        System.out.println("Current epoch " + _epoch + ", location " + getLocation());
        System.out.println("Done!");
    }

    public void stepRequest(int userId) {
        //System.out.println("[Request sent] Type: Step To: " + getUserURL(userId) + ", From: " + _id);
        _restTemplate.getForObject(getUserURL(userId)+ "/step/", Void.class);
    }


    /* ========================================================== */
    /* ====[             Request Location Proof             ]==== */
    /* ========================================================== */

    private LocationProof requestLocationProof(int userId) {
        Map<String, Integer> params = new HashMap<>();
        params.put("epoch", _epoch);
        params.put("proverId", _id);
        //System.out.println("[Request sent] Type: LocationProof To: " + getUserURL(userId) + ", From: " + _id + ", Epoch: " + _epoch);
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

    public LocationProof signLocationProof(ProofData proofData) throws Exception {
        byte[] proofBytes = writeValueAsBytes(proofData);
        String signature = RSAKeyGenerator.sign(proofBytes, _keyPair.getPrivate());
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

    public void reportLocation(int epoch, Location epochLocation) throws Exception {
        if (!(0 <= epoch && epoch <= _epoch))
            throw new IllegalArgumentException("Epoch must be positive and not exceed the current epoch.");

        List<LocationProof> epochProofs = getEpochProofs(epoch);
        LocationReport locationReport = new LocationReport(_id, epoch, epochLocation, epochProofs);
        System.out.println(locationReport);
        
        // encrypt using server public key, sign using client private key
        SecretKey secretKey = AESKeyGenerator.makeAESKey();
        byte[] bytes = writeValueAsBytes(locationReport);
        postToServer(bytes, secretKey, "/submit-location-report");
    }


    /* ========================================================== */
    /* ====[             Obtain Location Report             ]==== */
    /* ========================================================== */

    public SignedLocationReport checkObtainLocationResponse(SecureMessage secureResponse, SecretKey secretKey, int id, int epoch) throws Exception {
        // Check response's secret key for freshness
        if (!secureResponse.getSecretKey(_keyPair.getPrivate()).equals( secretKey ))
            throw new IllegalArgumentException("Server response not fresh!");

        // Decipher and check response signature
        byte[] messageBytes = secureResponse.decipherAndVerify(_keyPair.getPrivate(), _serverKey);
        SignedLocationReport report = SignedLocationReport.getFromBytes(messageBytes);

        // Check content
        if (report.get_userId() != id || report.get_epoch() != epoch)
            throw new IllegalArgumentException("Bad server response!");

        return report;
    }

    public LocationReport checkLocationReport(SignedLocationReport signedReport, PublicKey verifyKey) throws Exception {
        // Check report
        signedReport.verify(verifyKey);

        int validProofCount = signedReport.verifyProofs();
        if (validProofCount <= BYZANTINE_USERS)
            throw new ReportNotAcceptableException("Not enough proofs to constitute an acceptable Location Report");

        // Return safe report
        return signedReport.get_report();
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public LocationReport obtainReport(int epoch) throws Exception {
        if (!(0 <= epoch && epoch <= _epoch))
            throw new IllegalArgumentException("Epoch must be positive and not exceed the current epoch.");

        // Create request
        SecretKey secretKey = AESKeyGenerator.makeAESKey();
        ObtainLocationRequest locationRequest = new ObtainLocationRequest(_id, epoch);
        byte[] bytes = writeValueAsBytes(locationRequest);

        // Perform request
        SecureMessage secureResponse = postToServer(bytes, secretKey, "/obtain-location-report");

        // Check response
        if (secureResponse == null) return null;
        SignedLocationReport signedReport = checkObtainLocationResponse(secureResponse, secretKey, _id, epoch);
        LocationReport report = checkLocationReport(signedReport, _keyPair.getPublic());

        return report;
    }


    /* ========================================================== */
    /* ====[                Request My Proofs               ]==== */
    /* ========================================================== */

    public SecureMessage makeProofsRequest(int id, Set<Integer> epochs, SecretKey secretKey) throws Exception {
        WitnessProofsRequest witnessProofsRequest = new WitnessProofsRequest(id, epochs);
        byte[] bytes = writeValueAsBytes(witnessProofsRequest);
        return new SecureMessage(bytes, secretKey, _serverKey, _keyPair.getPrivate());
    }

    public List<LocationProof> checkRequestProofsResponse(SecureMessage secureResponse, SecretKey secretKey, int witnessId, Set<Integer> epochs) throws Exception {
        // Check response's secret key for freshness
        if (!secureResponse.getSecretKey(_keyPair.getPrivate()).equals( secretKey ))
            throw new IllegalArgumentException("Server response not fresh!");

        // Decipher and check response signature
        byte[] messageBytes = secureResponse.decipherAndVerify(_keyPair.getPrivate(), _serverKey);
        ObjectMapper objectMapper = new ObjectMapper();
        List<LocationProof> proofs = objectMapper.readValue(messageBytes, new TypeReference<>(){});

        // Check content
        for (LocationProof proof : proofs) //FIXME invalid proofs?
            if (proof.get_witnessId() != witnessId || !epochs.contains( proof.get_epoch() ))
                throw new IllegalArgumentException("Bad server response!");

        return proofs;
    }

    public List<LocationProof> requestMyProofs(Set<Integer> epochs) throws Exception {
        for (int epoch : epochs)
            if (!(0 <= epoch && epoch <= _epoch))
                throw new IllegalArgumentException("The epochs must be positive and not exceed the current epoch.");

        // Create request
        SecretKey secretKey = AESKeyGenerator.makeAESKey();
        WitnessProofsRequest witnessProofsRequest = new WitnessProofsRequest(_id, epochs);
        byte[] bytes = writeValueAsBytes(witnessProofsRequest);

        // Perform request
        SecureMessage secureResponse = postToServer(bytes, secretKey, "/request-proofs");

        // Check response
        List<LocationProof> proofs = checkRequestProofsResponse(secureResponse, secretKey, _id, epochs);
        for (LocationProof proof : proofs)
            proof.verify(_keyPair.getPublic()); // throws exception

        return proofs;
    }

}
