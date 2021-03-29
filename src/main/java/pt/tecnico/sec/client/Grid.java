package pt.tecnico.sec.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Grid {

    // singleton design pattern
    private static Grid _instance = null;

    // the environment
    private final int _nX = 10, _nY = 10;
    private final int _detectionRange = 2;
    private List<User> _users = new ArrayList<>();
    private int _epoch = 0;

    private Grid() {
        // empty constructor
    }

    public static synchronized Grid getInstance() {
        if (_instance == null)
            _instance = new Grid();
        return _instance;
    }

    public void join(User user) {
        // place in a random location inside the grid
        Random random = new Random();
        int x = random.nextInt(_nX);
        int y = random.nextInt(_nY);
        user.setLocation(x, y);
        // append to the list of users inside the grid
        _users.add(user);
    }

    private double distance(User user1, User user2) {
        int x1 = user1.getX();
        int y1 = user1.getY();
        int x2 = user2.getX();
        int y2 = user2.getY();
        // euclidean distance
        return Math.sqrt( Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) );
    }

    public List<User> findNearbyUsers(User user1) {
        List<User> nearbyUsers = new ArrayList<>();
        for (User user2 : _users) {
            // skip if comparing with the same user
            if (user2.equals(user1))
                continue;
            // append if user2 inside detection radios
            if (distance(user1, user2) <= _detectionRange)
                nearbyUsers.add(user2);
        }
        return nearbyUsers;
    }

    public void step() {
        // TODO : the visitor design pattern might be useful in the future
        for (User user : _users) {
            user.proveLocation();
        }
        _epoch += 1;
    }

}
