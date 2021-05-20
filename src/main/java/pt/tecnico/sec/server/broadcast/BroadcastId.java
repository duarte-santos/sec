package pt.tecnico.sec.server.broadcast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class BroadcastId {
    private int _senderId;
    private int _id;

    public BroadcastId() {
    }

    public BroadcastId(int _senderId, int _id) {
        this._senderId = _senderId;
        this._id = _id;
    }

    public int get_senderId() {
        return _senderId;
    }

    public void set_senderId(int _senderId) {
        this._senderId = _senderId;
    }

    public int get_id() {
        return _id;
    }

    public void set_id(int _id) {
        this._id = _id;
    }

    @Override
    public String toString() {
        return "BroadcastId{" +
                "_senderId=" + _senderId +
                ", _id=" + _id +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BroadcastId that = (BroadcastId) o;
        return _senderId == that._senderId && _id == that._id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_senderId, _id);
    }
}
