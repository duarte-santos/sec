package pt.tecnico.sec.client;

import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.CryptoRSA;
import pt.tecnico.sec.JavaKeyStore;
import pt.tecnico.sec.ObjectMapperHandler;
import pt.tecnico.sec.server.exception.ReportNotAcceptableException;

import javax.crypto.SecretKey;
import java.security.PublicKey;
import java.util.*;

import static pt.tecnico.sec.Constants.*;

public class User {

    private RestTemplate _restTemplate;
    private final int _id;
    private final int _serverCount;
    private final JavaKeyStore _keyStore;

    private Grid _prevGrid = null; // useful for synchronization
    private Grid _grid;
    private int _epoch = 0;
    private final Map<Integer, List<LocationProof>> _proofs =  new HashMap<>();

    private static final Map<Integer, SecretKey> _secretKeys = new HashMap<>();
    private static final Map<Integer, Integer> _sKeysCreationEpoch = new HashMap<>();

    public User(Grid grid, int id, int serverCount, JavaKeyStore keyStore) {
        _grid = grid;
        _id = id;
        _serverCount = serverCount;
        _keyStore = keyStore;
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

    public String getUserURL(int userId) {
        int port = CLIENT_BASE_PORT + userId;
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

    public int getRandomServerId() {
        Random random = new Random();
        return random.nextInt(_serverCount);
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
        byte[] proofBytes = ObjectMapperHandler.writeValueAsBytes(proofData);
        String signature = CryptoRSA.sign(proofBytes, _keyStore.getPersonalPrivateKey());
        return new LocationProof(proofData, signature);
    }

    public LocationProof makeLocationProof(int proverId, int proverEpoch) throws Exception {
        // check proximity
        Location witnessLoc;
        String type;
        if (proverEpoch == _epoch) { // users are synchronized
            witnessLoc = getLocation();
            type = isNearby(proverId) ? SUCCESS : FAILURE;
        } else if (proverEpoch == _epoch - 1) { // prover is not synchronized yet
            witnessLoc = getPrevLocation();
            type = wasNearby(proverId) ? SUCCESS : FAILURE;
        } else
            throw new IllegalArgumentException("Can only prove location requests regarding current or previous epoch");

        // build and sign proof
        ProofData proofData = new ProofData(witnessLoc, proverId, _id, proverEpoch, type);
        return signLocationProof(proofData);
    }


    /* ========================================================== */
    /* ====[             Submit Location Report             ]==== */
    /* ========================================================== */

    @SuppressWarnings("SameReturnValue")
    public String submitReport(int epoch, Location epochLocation) throws Exception {
        // Build report
        List<LocationProof> epochProofs = getEpochProofs(epoch);
        LocationReport locationReport = new LocationReport(_id, epoch, epochLocation, epochProofs);
        byte[] bytes = ObjectMapperHandler.writeValueAsBytes(locationReport);
        System.out.println(locationReport);

        // Send report
        byte[] responseBytes = postToServers(bytes, "/submit-location-report");

        // Check response
        if (responseBytes == null || !ObjectMapperHandler.getStringFromBytes(responseBytes).equals("OK"))
            throw new IllegalArgumentException("Bad server response!");
        return OK;
    }


    /* ========================================================== */
    /* ====[             Obtain Location Report             ]==== */
    /* ========================================================== */

    public LocationReport obtainReport(int epoch) throws Exception {
        // Create request
        ObtainLocationRequest locationRequest = new ObtainLocationRequest(_id, epoch);
        byte[] requestBytes = ObjectMapperHandler.writeValueAsBytes(locationRequest);

        // Perform request
        byte[] responseBytes = postToServers(requestBytes, "/obtain-location-report");

        // Check response
        if (responseBytes == null) return null;
        SignedLocationReport signedReport = checkObtainLocationResponse(responseBytes, _id, epoch);
        return checkLocationReport(signedReport, _keyStore.getPersonalPublicKey());
    }

    public SignedLocationReport checkObtainLocationResponse(byte[] responseBytes, int id, int epoch) throws Exception {
        SignedLocationReport report = SignedLocationReport.getFromBytes(responseBytes);

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


    /* ========================================================== */
    /* ====[             Obtain Witness Proofs              ]==== */
    /* ========================================================== */

    public List<LocationProof> obtainWitnessProofs(Set<Integer> epochs) throws Exception {
        // Create request
        WitnessProofsRequest witnessProofsRequest = new WitnessProofsRequest(_id, epochs);
        byte[] requestBytes = ObjectMapperHandler.writeValueAsBytes(witnessProofsRequest);

        // Perform request
        byte[] responseBytes = postToServers(requestBytes, "/request-proofs");

        // Check response
        List<LocationProof> proofs = checkWitnessProofsResponse(responseBytes, _id, epochs);
        for (LocationProof proof : proofs)
            proof.verify(_keyStore.getPersonalPublicKey());

        return proofs;
    }

    public List<LocationProof> checkWitnessProofsResponse(byte[] responseBytes, int witnessId, Set<Integer> epochs) throws Exception {
        if (responseBytes == null)
            throw new IllegalArgumentException("Bad server response!");

        List<LocationProof> proofs = ObjectMapperHandler.getLocationProofListFromBytes(responseBytes);

        // Check content
        for (LocationProof proof : proofs) //FIXME invalid proofs?
            if (proof.get_witnessId() != witnessId || !epochs.contains( proof.get_epoch() ))
                throw new IllegalArgumentException("Bad server response!");

        return proofs;
    }


    /* ========================================================== */
    /* ====[              Server communication              ]==== */
    /* ========================================================== */

    private byte[] sendRequest(int serverId, SecureMessage secureRequest, SecretKey secretKey, String endpoint) throws Exception {
        PublicKey serverKey = _keyStore.getPublicKey("server" + serverId);
        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + endpoint, request, SecureMessage.class);

        if (secureResponse == null) return null;

        // Check response's signature and decipher TODO freshness
        return secureResponse.decipherAndVerify( secretKey, serverKey);
    }

    private byte[] postToServers(byte[] messageBytes, String endpoint) throws Exception {
        int serverId = getRandomServerId(); // Choose random server to send request
        SecretKey secretKey = updateSecretKey(serverId);
        System.out.println(_keyStore.toString());
        System.out.println("Requesting from server " + serverId);
        SecureMessage secureRequest = new SecureMessage(_id, messageBytes, secretKey, _keyStore.getPersonalPrivateKey());
        return sendRequest(serverId, secureRequest, secretKey, endpoint);
    }

    private byte[] postKeyToServer(int serverId, SecretKey keyToSend) throws Exception {
        PublicKey serverKey = _keyStore.getPublicKey("server" + serverId);
        SecureMessage secureRequest = new SecureMessage(_id, keyToSend, serverKey, _keyStore.getPersonalPrivateKey());
        return sendRequest(serverId, secureRequest, keyToSend, "/secret-key");
    }


    /* ========================================================== */
    /* ====[               Handle Secret Keys               ]==== */
    /* ========================================================== */

    public boolean secretKeyValid(int serverId) {
        return _secretKeys.get(serverId) != null && _epoch - _sKeysCreationEpoch.get(serverId) <= SECRET_KEY_DURATION;
    }

    public SecretKey updateSecretKey(int serverId) throws Exception {

        if (!secretKeyValid(serverId)) {
            System.out.print("Generating new secret key...");

            // Generate secret key
            SecretKey newSecretKey = AESKeyGenerator.makeAESKey();

            // Send key
            byte[] responseBytes = postKeyToServer(serverId, newSecretKey);

            // Check response
            if (responseBytes == null || !ObjectMapperHandler.getStringFromBytes(responseBytes).equals(OK))
                throw new IllegalArgumentException("Error exchanging new secret key");

            // Success! Update key
            _secretKeys.put(serverId, newSecretKey);
            _sKeysCreationEpoch.put(serverId, _epoch);

            System.out.println("Done!");
        }

        return _secretKeys.get(serverId);
    }

}
