package pt.tecnico.sec.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BroadcastWrite {

    private BroadcastId _broadcastId;
    private DBLocationReport _report;

    public BroadcastWrite() {
        // empty
    }

    public BroadcastWrite(BroadcastId _broadcastId, DBLocationReport _report) {
        this._broadcastId = _broadcastId;
        this._report = _report;
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

    public DBLocationReport get_report() {
        return _report;
    }

    public void set_report(DBLocationReport _report) {
        this._report = _report;
    }

    @Override
    public String toString() {
        return "BroadcastWrite{" +
                "_broadcastId=" + _broadcastId +
                ", _report=" + _report +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BroadcastWrite that = (BroadcastWrite) o;
        return Objects.equals(_broadcastId, that._broadcastId) && Objects.equals(_report, that._report);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_broadcastId, _report);
    }
}
