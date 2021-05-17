package pt.tecnico.sec.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.client.ObtainLocationRequest;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BroadcastRead {

    private int _originalId;
    private ObtainLocationRequest _locationRequest;

    public BroadcastRead(int _originalId, ObtainLocationRequest _locationRequest) {
        this._originalId = _originalId;
        this._locationRequest = _locationRequest;
    }

    public BroadcastRead() {
        // empty
    }

    public int get_originalId() {
        return _originalId;
    }

    public void set_originalId(int _originalId) {
        this._originalId = _originalId;
    }

    public ObtainLocationRequest get_locationRequest() {
        return _locationRequest;
    }

    public void set_locationRequest(ObtainLocationRequest _locationRequest) {
        this._locationRequest = _locationRequest;
    }

    @Override
    public String toString() {
        return "BroadcastRead{" +
                "_originalId=" + _originalId +
                ", _locationRequest=" + _locationRequest +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BroadcastRead that = (BroadcastRead) o;
        return _originalId == that._originalId && Objects.equals(_locationRequest, that._locationRequest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_originalId, _locationRequest);
    }

}
