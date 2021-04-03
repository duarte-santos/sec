package pt.tecnico.sec.client;

public class Location {
    private final int _x;
    private final int _y;

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
}
