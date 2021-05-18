package pt.tecnico.sec.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.client.ObtainLocationRequest;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BroadcastRead {

    private BroadcastId _broadcastId;
    private ObtainLocationRequest _locationRequest;

    public BroadcastRead() {
        // empty
    }

    public BroadcastRead(BroadcastId _broadcastId, ObtainLocationRequest _locationRequest) {
        this._broadcastId = _broadcastId;
        this._locationRequest = _locationRequest;
    }

    public int get_originalId() {
        return _broadcastId.get_senderId();
    }

    public BroadcastId get_broadcastId() {
        return _broadcastId;
    }

    public void set_broadcastId(BroadcastId _broadcastId) {
        this._broadcastId = _broadcastId;
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
                "_broadcastId=" + _broadcastId +
                ", _locationRequest=" + _locationRequest +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BroadcastRead that = (BroadcastRead) o;
        return Objects.equals(_broadcastId, that._broadcastId) && Objects.equals(_locationRequest, that._locationRequest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_broadcastId, _locationRequest);
    }
}
