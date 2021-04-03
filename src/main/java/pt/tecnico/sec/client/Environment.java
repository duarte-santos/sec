package pt.tecnico.sec.client;

import java.util.*;

public class Environment {

    // Map Epoch -> Grid
    private Map<Integer, Grid> _environment = new HashMap<>();

    public Environment() {
        // empty constructor
    }

    public Grid getGrid(int epoch) {
        return _environment.get(epoch);
    }

    public int getMaxEpoch() {
        Set<Integer> epochs = _environment.keySet();
        return Collections.max(epochs);
    }

    public List<Integer> getUserList() {
        return new ArrayList<>( _environment.get(0).getAllUserIds() );
    }

    public void addEpochGrid(int epoch, Grid grid) {
        _environment.put(epoch, grid);
    }


    public Location getUserLocation(int epoch, int userId) {
        return _environment.get(epoch).getUserLocation(userId);
    }



}
