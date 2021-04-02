package pt.tecnico.sec.client;

public class Location {
    private int _x;
    private int _y;

    public Location(int x, int y) {
        _x = x;
        _y = y;
    }

    public int getX() {
        return _x;
    }

    public void setX(int x) {
        _x = x;
    }

    public int getY() {
        return _y;
    }

    public void setY(int y) {
        _y = y;
    }

    public double distance(Location location) {
        int x1 = _x;
        int y1 = _y;
        int x2 = location.getX();
        int y2 = location.getY();
        // euclidean distance
        return Math.sqrt( Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) );
    }

    @Override
    public String toString() {
        return "[" + _x + ", " + _y + "]";
    }
}
