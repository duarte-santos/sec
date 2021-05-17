package pt.tecnico.sec.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BroadcastWrite {

    private int _originalId;
    private DBLocationReport _report;

    public BroadcastWrite(int _originalId, DBLocationReport _report) {
        this._originalId = _originalId;
        this._report = _report;
    }

    public BroadcastWrite() {
        // empty
    }

    public int get_originalId() {
        return _originalId;
    }

    public void set_originalId(int _originalId) {
        this._originalId = _originalId;
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
                "_originalId=" + _originalId +
                ", _report=" + _report +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BroadcastWrite that = (BroadcastWrite) o;
        return _originalId == that._originalId && Objects.equals(_report, that._report);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_originalId, _report);
    }

}
