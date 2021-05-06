package pt.tecnico.sec.client;

import java.util.*;

public class Environment {

    // Map Epoch -> Grid
    private final Map<Integer, Grid> _environment = new HashMap<>();
    private final int _serverCount;

    public Environment(int serverCount) {
        _serverCount = serverCount;
    }

    public Grid getGrid(int epoch) {
        return _environment.get(epoch);
    }

    public int getMaxEpoch() {
        Set<Integer> epochs = _environment.keySet();
        return Collections.max(epochs);
    }

    public List<Integer> getUserList() {
        return getGrid(0).getUserList();
    }

    public void addEpochGrid(int epoch, Grid grid) {
        _environment.put(epoch, grid);
    }

    public int getServerCount() {
        return _serverCount;
    }

}
