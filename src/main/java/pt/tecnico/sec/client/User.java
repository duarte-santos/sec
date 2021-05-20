package pt.tecnico.sec.client;

import org.apache.commons.codec.binary.Hex;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.CryptoRSA;
import pt.tecnico.sec.JavaKeyStore;
import pt.tecnico.sec.ObjectMapperHandler;
import pt.tecnico.sec.server.exception.ReportNotAcceptableException;

import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
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
        locationReport.removeInvalidProofs( _keyStore.getAllUsersPublicKeys(_id) );
        System.out.println(locationReport);

        SignedLocationReport signedLocationReport = new SignedLocationReport(locationReport, _keyStore.getPersonalPrivateKey());
        Message request = new Message(0, signedLocationReport);

        // Send report
        Message response = postToServers(request, "/submit-location-report");

        // Check response
        response.throwIfException();
        if (!response.isOKString())
            throw new IllegalArgumentException("Bad server response!");

        return OK;
    }

    /* ========================================================== */
    /* ====[             Obtain Location Report             ]==== */
    /* ========================================================== */

    public LocationReport obtainReport(int epoch) throws Exception {
        // Create request
        ObtainLocationRequest locationRequest = new ObtainLocationRequest(_id, epoch);
        Message request = new Message(0, locationRequest);

        // Perform request
        Message response = postToServers(request, "/obtain-location-report");

        // Check response
        if (response.get_data() == null) return null;
        response.throwIfException();
        SignedLocationReport signedReport = checkObtainLocationResponse(response, _id, epoch);
        return checkLocationReport(signedReport, _keyStore.getPersonalPublicKey());
    }

    public SignedLocationReport checkObtainLocationResponse(Message response, int id, int epoch) {
        SignedLocationReport report = response.retrieveSignedLocationReport();
        // Check content
        if (report.get_userId() != id || report.get_epoch() != epoch)
            throw new IllegalArgumentException("Bad server response!");
        return report;
    }

    public LocationReport checkLocationReport(SignedLocationReport signedReport, PublicKey verifyKey) throws Exception {
        // Check report
        signedReport.verify(verifyKey);
        try {
            signedReport.verifyProofs( _keyStore.getAllUsersPublicKeys(_id) );
        } catch (ReportNotAcceptableException e) {
            throw new IllegalArgumentException("Bad server response!");
        }

        // Return safe report
        return signedReport.get_report();
    }


    /* ========================================================== */
    /* ====[             Obtain Witness Proofs              ]==== */
    /* ========================================================== */

    public List<LocationProof> obtainWitnessProofs(Set<Integer> epochs) throws Exception {
        // Create request
        WitnessProofsRequest witnessProofsRequest = new WitnessProofsRequest(_id, epochs);
        Message request = new Message(0, witnessProofsRequest);

        // Perform request
        Message response = postToServers(request, "/request-proofs");

        // Check response
        response.throwIfException();
        List<LocationProof> proofs = checkWitnessProofsResponse(response, _id, epochs);
        for (LocationProof proof : proofs)
            proof.verify(_keyStore.getPersonalPublicKey());

        return proofs;
    }

    public List<LocationProof> checkWitnessProofsResponse(Message response, int witnessId, Set<Integer> epochs) {
        List<LocationProof> proofs = response.retrieveLocationProofList();
        // Check content
        for (LocationProof proof : proofs)
            if (proof.get_witnessId() != witnessId || !epochs.contains( proof.get_epoch() ))
                throw new IllegalArgumentException("Bad server response!");
        return proofs;
    }


    /* ========================================================== */
    /* ====[              Server communication              ]==== */
    /* ========================================================== */

    private Message sendRequest(int serverId, SecureMessage secureRequest, SecretKey secretKey, String endpoint) throws Exception {
        PublicKey serverKey = _keyStore.getPublicKey("server" + serverId);
        HttpEntity<SecureMessage> request = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + endpoint, request, SecureMessage.class);

        if (secureResponse == null) return null;

        // Check response's signature and decipher TODO freshness
        return secureResponse.decipherAndVerifyMessage( secretKey, serverKey);
    }

    private Message postToServers(Message message, String endpoint) throws Exception {
        byte[] messageBytes = ObjectMapperHandler.writeValueAsBytes(message);
        messageBytes = solvePuzzle(messageBytes);

        int serverId = getRandomServerId(); // Choose random server to send request
        SecretKey secretKey = updateSecretKey(serverId);
        System.out.println("Requesting from server " + serverId);
        SecureMessage secureRequest = new SecureMessage(_id, messageBytes, secretKey, _keyStore.getPersonalPrivateKey());
        return sendRequest(serverId, secureRequest, secretKey, endpoint);
    }

    private Message postKeyToServer(int serverId, SecretKey keyToSend) throws Exception {
        PublicKey serverKey = _keyStore.getPublicKey("server" + serverId);
        PrivateKey myKey = _keyStore.getPersonalPrivateKey();
        SecureMessage secureRequest = new SecureMessage(_id, keyToSend, serverKey, myKey);

        HttpEntity<SecureMessage> httpRequest = new HttpEntity<>(secureRequest);
        SecureMessage secureResponse = _restTemplate.postForObject(getServerURL(serverId) + "/secret-key", httpRequest, SecureMessage.class);

        // Check response's signature and decipher TODO freshness
        assert secureResponse != null;
        return secureResponse.decipherAndVerifyMessage( myKey, serverKey);
    }


    /* ========================================================== */
    /* ====[               Handle Secret Keys               ]==== */
    /* ========================================================== */

    public boolean secretKeyValid(int serverId) {
        Integer creation = _sKeysCreationEpoch.get(serverId);
        return creation != null && (_epoch - creation) <= SECRET_KEY_DURATION;
    }

    public SecretKey updateSecretKey(int serverId) throws Exception {
        if (!secretKeyValid(serverId)) {
            System.out.print("Generating new secret key...");

            // Generate secret key
            SecretKey newSecretKey = AESKeyGenerator.makeAESKey();

            // Send key
            Message response = postKeyToServer(serverId, newSecretKey);

            // Check response
            response.throwIfException();
            if (!response.isOKString())
                throw new IllegalArgumentException("Error exchanging new secret key");

            // Success! Update key
            _keyStore.setAndStoreSecretKey("server" + serverId, newSecretKey);
            _sKeysCreationEpoch.put(serverId, _epoch);

            System.out.println("Done!");
        }

        return _keyStore.getSecretKey("server" + serverId);
    }

    /* ========================================================== */
    /* ====[                 Proof of Work                  ]==== */
    /* ========================================================== */

    public byte[] solvePuzzle(byte[] message) throws NoSuchAlgorithmException {

        System.out.println("Generating proof of work...");

        int i;
        byte[] nonce;
        byte[] messageWithNonce;
        byte[] hash;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        // Try to find the nonce to get n leading zeros
        for (i=0; ;i++){

            nonce = ByteBuffer.allocate(POW_N+2).putInt(i).array();
            messageWithNonce = new byte[message.length + nonce.length];
            System.arraycopy(message, 0, messageWithNonce,0, message.length);
            System.arraycopy(nonce, 0, messageWithNonce, message.length, nonce.length);

            hash = digest.digest(messageWithNonce);

            // Check if it has n leading 0s
            boolean found = true;
            for (int k = 0; k < POW_N; k++){
                if (hash[k] != 0) {
                    found = false;
                    break;
                }
            }
            if (found) break;

        }

        System.out.println("Number of iterations: " + i);
        System.out.println("Generated nounce: " + Hex.encodeHexString(nonce));
        System.out.println("Message hash: " + Hex.encodeHexString(hash));
        return messageWithNonce;
    }

}
