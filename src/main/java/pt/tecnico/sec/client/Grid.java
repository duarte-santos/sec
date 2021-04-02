package pt.tecnico.sec.client;

import java.util.*;

public class Grid {
    // Constants
    private static final int DetectionRange = 2;

    // Map UserID -> Location
    private Map<Integer, Location> _grid = new HashMap<>();

    public Grid() {
        // empty constructor
    }

    public void setGrid(Map<Integer, Location> grid) {
        _grid = grid;
    }

    public void addUserLocation(int userId, Location location) {
        //TODO checks
        _grid.put(userId, location);
    }

    public Set<Integer> getAllUserIds() {
        return _grid.keySet();
    }

    public Location getUserLocation(int userId) {
        return _grid.get(userId);
    }

    private double distance(int userId1, int userId2) {
        Location l1 = getUserLocation(userId1);
        Location l2 = getUserLocation(userId2);
        return l1.distance(l2);
    }

    public boolean isNearby(int userId1, int userId2) {
        return distance(userId1, userId2) <= DetectionRange;
    }

    public List<Integer> findNearbyUsers(int userId1) {
        List<Integer> nearbyUsers = new ArrayList<>();
        for (int userId2 : getAllUserIds()) {
            // skip if comparing with the same user
            if (userId2 == userId1)
                continue;
            // append if user2 inside detection radios
            if (isNearby(userId1, userId2))
                nearbyUsers.add(userId2);
        }
        return nearbyUsers;
    }

}
