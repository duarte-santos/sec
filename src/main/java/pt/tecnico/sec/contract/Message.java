package pt.tecnico.sec.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.client.report.LocationProof;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static pt.tecnico.sec.Constants.OK;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {

    private Long _nounce;
    private Object _data;

    public Message() {
    }

    public Message(Object data) {
        _nounce = System.currentTimeMillis();
        _data = data;
    }

    public Long get_nounce() {
        return _nounce;
    }

    public void set_nounce(Long _nounce) {
        this._nounce = _nounce;
    }

    public Object get_data() {
        return _data;
    }

    public void set_data(Object _data) {
        this._data = _data;
    }

    @JsonIgnore
    public Long checkNounce(Long prevNounce) {
        if (_nounce == null || (prevNounce != null && _nounce < prevNounce))
            throw new IllegalArgumentException("Message not fresh!");
        System.out.println("Message is fresh!");
        return _nounce;
    }

    @Override
    public String toString() {
        return "Message{" +
                "_nounce=" + _nounce +
                ", _data=" + _data +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return Objects.equals(_nounce, message._nounce) && Objects.equals(_data, message._data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_nounce, _data);
    }

    /* ========================================================== */
    /* ====[                  Data casts                    ]==== */
    /* ========================================================== */

    public void throwIfException() throws Exception {
        if (_data == null) return;
        if (_data instanceof Exception)
            throw (Exception) _data;
    }

    public SecretKey retrieveSecretKey() {
        if (!(_data instanceof SecretKey))
            throw new IllegalArgumentException("Message does not contain a SecretKey.");
        return (SecretKey) _data;
    }

    public SignedLocationReport retrieveSignedLocationReport() {
        if (!(_data instanceof SignedLocationReport))
            throw new IllegalArgumentException("Message does not contain a SignedLocationReport.");
        return (SignedLocationReport) _data;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isOKString() {
        if (_data == null) return false;
        return (_data instanceof String) && _data.equals(OK);
    }

    public ObtainLocationRequest retrieveObtainLocationRequest() {
        if (!(_data instanceof ObtainLocationRequest))
            throw new IllegalArgumentException("Message does not contain a ObtainLocationRequest.");
        return (ObtainLocationRequest) _data;
    }

    public WitnessProofsRequest retrieveWitnessProofsRequest() {
        if (!(_data instanceof WitnessProofsRequest))
            throw new IllegalArgumentException("Message does not contain a WitnessProofsRequest.");
        return (WitnessProofsRequest) _data;
    }

    public List<LocationProof> retrieveLocationProofList() {
        List<LocationProof> res = new ArrayList<>();
        if (!(_data instanceof ArrayList))
            throw new IllegalArgumentException("Message does not contain a List<LocationProof>.");
        List<?> list = (ArrayList<?>) _data;
        for (Object o : list) {
            if (!(o instanceof LocationProof))
                throw new IllegalArgumentException("Message does not contain a List<LocationProof>.");
            res.add((LocationProof) o);
        }
        return res;
    }

    public ObtainUsersRequest retrieveObtainUsersRequest() {
        if (!(_data instanceof ObtainUsersRequest))
            throw new IllegalArgumentException("Message does not contain a ObtainUsersRequest.");
        return (ObtainUsersRequest) _data;
    }

    public UsersAtLocation retrieveUsersAtLocation() {
        if (!(_data instanceof UsersAtLocation))
            throw new IllegalArgumentException("Message does not contain a UsersAtLocation.");
        return (UsersAtLocation) _data;
    }



}
