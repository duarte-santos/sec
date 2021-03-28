package pt.tecnico.sec.client;

import java.util.ArrayList;
import java.util.List;

public class Grid {

    /* Singleton design pattern */
    private static Grid _instance = null;

    /* The environment */
    private int _nX, _nY;
    private boolean[][] _grid;
    private List<User> _users;

    private Grid(int nX, int nY) {
        _nX = nX;
        _nY = nY;

        /*** create grid ***/
        _grid = new boolean[_nX][_nY];
        for (int x = 0; x < _nX; x++)
            for (int y = 0; y < _nY; y++)
                _grid[x][y] = false;

        /*** create users ***/
        _users = new ArrayList<>();
    }

    public static synchronized Grid getInstance() {
        if (_instance == null)
            _instance = new Grid(10, 10);
        return _instance;
    }

    public void join(User user) {
        // TODO : assign random location
        _users.add(user);
    }

}
