package pt.tecnico.sec.server;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class Location {

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Integer id;

    private int _x;
    private int _y;

    public Location() {}

    public Location(int x, int y) {
        _x = x;
        _y = y;
    }

    public int getX() {
        return _x;
    }

    public int getY() {
        return _y;
    }

    public double distance(Location location) {
        int x = location.getX();
        int y = location.getY();
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
