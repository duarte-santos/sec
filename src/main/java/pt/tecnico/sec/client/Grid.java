package pt.tecnico.sec.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static pt.tecnico.sec.Constants.DETECTION_RANGE;

public class Grid {
    // Map UserID -> Location
    private final Map<Integer, Location> _grid = new HashMap<>();

    public Grid() {
        // empty constructor
    }

    public void addUserLocation(int userId, Location location) {
        _grid.put(userId, location);
    }

    public List<Integer> getUserList() {
        return new ArrayList<>( _grid.keySet() );
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
        return distance(userId1, userId2) <= DETECTION_RANGE;
    }

    public List<Integer> findNearbyUsers(int userId1) {
        List<Integer> nearbyUsers = new ArrayList<>();
        for (int userId2 : getUserList()) {
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
