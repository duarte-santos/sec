package pt.tecnico.sec.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Set;

@SuppressWarnings("unused")
public class WitnessProofsRequest {
    private int _userId;
    private Set<Integer> _epochs;

    public WitnessProofsRequest() {}

    public WitnessProofsRequest(int userId, Set<Integer> epochs) {
        this._userId = userId;
        this._epochs = epochs;
    }

    // convert from bytes
    public static WitnessProofsRequest getFromBytes(byte[] requestBytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(requestBytes, WitnessProofsRequest.class);
    }

    public int get_userId() {
        return _userId;
    }

    public void set_userId(int _userId) {
        this._userId = _userId;
    }

    public Set<Integer> get_epochs() {
        return _epochs;
    }

    public void set_epochs(Set<Integer> _epochs) {
        this._epochs = _epochs;
    }

    public void checkSender(int sender_id) {
        if ( sender_id != _userId )
            throw new IllegalArgumentException("Cannot request proofs from other users");
    }

    @Override
    public String toString() {
        return "WitnessProofsRequest{" +
                "_userId=" + _userId +
                ", _epochs=" + _epochs +
                '}';
    }
}
