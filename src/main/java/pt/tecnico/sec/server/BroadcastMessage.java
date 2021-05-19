package pt.tecnico.sec.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.client.ObtainLocationRequest;

import java.util.Objects;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class BroadcastMessage {
    private BroadcastId _broadcastId;
    private DBLocationReport _report = null;
    private Integer _timestamp = null;
    private ObtainLocationRequest _request = null;

    public BroadcastMessage() {}

    public BroadcastMessage(BroadcastId id, DBLocationReport report){
        _broadcastId = id;
        _report = report;
    }

    public BroadcastMessage(BroadcastId id, Integer timestamp){
        _broadcastId = id;
        _timestamp = timestamp;
    }

    public BroadcastMessage(BroadcastId id, ObtainLocationRequest request){
        _broadcastId = id;
        _request = request;
    }

    public Integer get_originalId() {
        return _broadcastId.get_senderId();
    }

    public BroadcastId get_broadcastId() {
        return _broadcastId;
    }

    public void set_broadcastId(BroadcastId _broadcastId) {
        this._broadcastId = _broadcastId;
    }

    public DBLocationReport getDBLocationReport() {
        return _report;
    }

    public Integer getTimestamp(){
        return _timestamp;
    }

    public ObtainLocationRequest getObtainLocationRequest() {
        return _request;
    }

    public DBLocationReport get_report() {
        return _report;
    }

    public void set_report(DBLocationReport _report) {
        this._report = _report;
    }

    public Integer get_timestamp() {
        return _timestamp;
    }

    public void set_timestamp(Integer _timestamp) {
        this._timestamp = _timestamp;
    }

    public ObtainLocationRequest get_request() {
        return _request;
    }

    public void set_request(ObtainLocationRequest _request) {
        this._request = _request;
    }

    public boolean is_write() {
        return _report != null && _timestamp == null && _request == null;
    }

    public boolean is_read() {
        return _request != null && _timestamp == null && _report == null;
    }

    @Override
    public String toString() {
        return "BroadcastMessage{" +
                "_broadcastId=" + _broadcastId +
                ((_report != null) ? ", _report=" + _report : "") +
                ((_timestamp != null) ? ", _report=" + _timestamp : "") +
                ((_request != null) ? ", _report=" + _request : "") +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BroadcastMessage that = (BroadcastMessage) o;
        return Objects.equals(_broadcastId, that._broadcastId) && Objects.equals(_report, that._report) && Objects.equals(_timestamp, that._timestamp) && Objects.equals(_request, that._request);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_broadcastId, _report, _timestamp, _request);
    }
}
