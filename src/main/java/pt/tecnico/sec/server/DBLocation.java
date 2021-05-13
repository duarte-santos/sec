package pt.tecnico.sec.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.client.Location;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
public class DBLocation {

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Integer id;

    private int _x;
    private int _y;

    public DBLocation() {}

    public DBLocation(int x, int y) {
        _x = x;
        _y = y;
    }

    // convert from client version
    public DBLocation(Location location) {
        _x = location.get_x();
        _y = location.get_y();
    }

    public int getX() {
        return _x;
    }

    public int getY() {
        return _y;
    }

    public double distance(DBLocation DBLocation) {
        int x = DBLocation.getX();
        int y = DBLocation.getY();
        // euclidean distance
        return Math.sqrt( Math.pow(x - _x, 2) + Math.pow(y - _y, 2) );
    }

    @Override
    public String toString() {
        return "[" + _x + ", " + _y + "]";
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int get_x() {
        return _x;
    }

    public void set_x(int _x) {
        this._x = _x;
    }

    public int get_y() {
        return _y;
    }

    public void set_y(int _y) {
        this._y = _y;
    }
}
