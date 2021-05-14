package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.server.DBLocation;

import java.util.Objects;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Location {
    private int _x;
    private int _y;

    public Location() {}

    public Location(int x, int y) {
        _x = x;
        _y = y;
    }

    // convert from server version
    public Location(DBLocation dbLocation) {
        _x = dbLocation.get_x();
        _y = dbLocation.get_y();
    }

    public int get_x() {
        return _x;
    }

    public int get_y() {
        return _y;
    }

    public void set_x(int _x) {
        this._x = _x;
    }

    public void set_y(int _y) {
        this._y = _y;
    }

    public double distance(Location location) {
        int x = location.get_x();
        int y = location.get_y();
        // euclidean distance
        return Math.sqrt( Math.pow(x - _x, 2) + Math.pow(y - _y, 2) );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (o.getClass() == Location.class) {
            Location that = (Location) o;
            return _x == that.get_x() && _y == that.get_y();
        }
        if (o.getClass() == DBLocation.class) {
            DBLocation that = (DBLocation) o;
            return _x == that.get_x() && _y == that.get_y();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_x, _y);
    }

    @Override
    public String toString() {
        return "[" + _x + ", " + _y + "]";
    }
}
