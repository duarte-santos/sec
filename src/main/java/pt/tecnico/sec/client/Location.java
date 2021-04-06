package pt.tecnico.sec.client;

public class Location {
    private int _x;
    private int _y;

    public Location(int x, int y) {
        _x = x;
        _y = y;
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
    public String toString() {
        return "[" + _x + ", " + _y + "]";
    }
}
