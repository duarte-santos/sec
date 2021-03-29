package pt.tecnico.sec.client;

public class User {

    private final Grid _grid;
    private int _x, _y;
    private int _id;

    public User() {
        _grid = Grid.getInstance();
        _grid.join(this);
    }

    public void setLocation(int x, int y) {
        _x = x;
        _y = y;
    }

    public int getX() {
        return _x;
    }

    public int getY() {
        return _y;
    }

    public void proveLocation() {
        // TODO
    }
}
