package pt.tecnico.sec.client;

import java.util.*;

public class Grid {
    // constants
    private static final int DetectionRange = 2;
    private static final int Size = 10;

    // singleton design pattern
    private static Grid _instance = null;

    // the environment
    private final int _nX = Size;
    private final int _nY = Size;
    private final Map<Integer, User> _users = new HashMap<>();
    private int _epoch = 0;
    private int _idCount = 0;

    private Grid() {
        // empty constructor
    }

    public static synchronized Grid getInstance() {
        if (_instance == null)
            _instance = new Grid();
        return _instance;
    }

    public int getNX() {
        return _nX;
    }

    public int getNY() {
        return _nY;
    }

    public int getEpoch() {
        return _epoch;
    }

    public synchronized void join(User user) {
        // place in a random location inside the grid
        Random random = new Random();
        int x = random.nextInt(_nX);
        int y = random.nextInt(_nY);
        user.setLocation(x, y);
        user.setId(_idCount);

        // append to the hashmap of users inside the grid
        _users.put(_idCount, user);

        _idCount++;
    }

    public User findUser(int id) {
        return _users.get(id);
    }

    private double distance(User user1, User user2) {
        Location l1 = user1.getLocation();
        Location l2 = user2.getLocation();
        return l1.distance(l2);
    }

    public boolean isNearby(User user1, User user2) {
        return distance(user1, user2) <= DetectionRange;
    }

    public boolean isNearby(int id1, int id2) {
        return isNearby(findUser(id1), findUser(id2));
    }

    public List<User> findNearbyUsers(User user1) {
        List<User> nearbyUsers = new ArrayList<>();
        for (User user2 : _users.values()) {
            // skip if comparing with the same user
            if (user2.equals(user1))
                continue;
            // append if user2 inside detection radios
            if (isNearby(user1, user2))
                nearbyUsers.add(user2);
        }
        return nearbyUsers;
    }

    public synchronized void step() {
        // TODO : the visitor design pattern might be useful in the future
        for (User user : _users.values()) {
            user.proveLocation();
        }
        _epoch += 1;
    }

}
