package pt.tecnico.sec.server;

import pt.tecnico.sec.ObjectMapperHandler;

import java.util.HashMap;
import java.util.Map;

import static pt.tecnico.sec.Constants.FAULTS;

public class BroadcastService {
    private final ServerApplication _serverApp;
    private final BroadcastId _broadcastId;
    private final int _serverCount;

    private boolean _sentEcho = false;
    private boolean _sentReady = false;
    private boolean _delivered = false;
    private final BroadcastWrite[] _echos;
    private final BroadcastWrite[] _readys;

    Integer[] _delivers;
    int _my_ts;

    BroadcastService(ServerApplication serverApp, BroadcastId broadcastId) {
        _serverApp = serverApp;
        _broadcastId = broadcastId;
        _serverCount = _serverApp.getServerCount();
        _echos = new BroadcastWrite[_serverCount];
        _readys = new BroadcastWrite[_serverCount];
    }

    /* ========================================================== */
    /* ====[              Auxiliary functions               ]==== */
    /* ========================================================== */

    public boolean is_delivered() {
        return _delivered;
    }

    public int countAcks() {
        int acks = 0;
        for (Integer timestamp : _delivers)
            if (timestamp != null) acks++;
        return acks;
    }

    public BroadcastWrite searchForMajorityMessage(BroadcastWrite[] messages, int quorum) {
        // count the times a message appears
        Map<BroadcastWrite, Integer> counter = new HashMap<>();
        for (BroadcastWrite m : messages) {
            if (m == null) continue;
            Integer count = counter.get(m);
            if (count == null) counter.put(m, 1);
            else counter.replace(m, count+1);
        }
        // search for message that appears more than <quorum> times
        for (BroadcastWrite m : counter.keySet()) {
            if (counter.get(m) > quorum) return m;
        }
        return null;
    }

    public boolean validResponse(int timestamp) {
        return timestamp == _my_ts;
    }

    /* ========================================================== */
    /* ====[              Double Echo Broadcast             ]==== */
    /* ========================================================== */

    // Send SEND
    public void broadcastSend(DBLocationReport report) throws Exception {
        _delivers = new Integer[_serverCount];

        _my_ts = report.get_timestamp() + 1; //FIXME timestamp of the report??
        report.set_timestamp(_my_ts);
        BroadcastWrite bw = new BroadcastWrite(_broadcastId, report);
        byte[] m = ObjectMapperHandler.writeValueAsBytes(bw);

        System.out.println("Broadcasting write...");
        _serverApp.postToServers(m, "/doubleEchoBroadcast-send");
        while (countAcks() <= (_serverCount + FAULTS) / 2) {
            // empty
        }
    }

    // Deliver SEND -> Send ECHO
    public void broadcastSENDDeliver(BroadcastWrite bw) throws Exception {
        if (!_sentEcho) {
            byte[] m = ObjectMapperHandler.writeValueAsBytes(bw);
            _sentEcho = true;
            _serverApp.postToServers(m, "/doubleEchoBroadcast-echo");
        }
    }

    // Deliver ECHO -> Send READY
    public void broadcastECHODeliver(int id, BroadcastWrite bw) throws Exception {
        if (_echos[id] == null) _echos[id] = bw;

        if (!_sentReady) {
            BroadcastWrite message = searchForMajorityMessage(_echos, (_serverCount+FAULTS)/2);
            if (message != null) {
                _sentReady = true;
                byte[] m = ObjectMapperHandler.writeValueAsBytes(message);
                _serverApp.postToServers(m, "/doubleEchoBroadcast-ready");
            }
        }
    }

    // Deliver READY -> Send READY, Deliver response
    public boolean broadcastREADYDeliver(int id, BroadcastWrite bw) throws Exception {
        if (_readys[id] == null) _readys[id] = bw;
        if (!_sentReady) {
            BroadcastWrite message = searchForMajorityMessage(_readys, FAULTS); // FIXME : f is faults or byzantines?
            if (message != null) {
                _sentReady = true;
                byte[] m = ObjectMapperHandler.writeValueAsBytes(message);
                _serverApp.postToServers(m, "/doubleEchoBroadcast-ready");
            }
            else return false; // for efficiency
        }
        if (!_delivered) {
            BroadcastWrite message = searchForMajorityMessage(_readys, 2*FAULTS); // FIXME : f is faults or byzantines?
            if (message != null) {
                _delivered = true;
                return true;
            }
        }
        return false;
    }

    // Received answer
    public void broadcastDeliver(int id, int timestamp) {
        if (_delivers[id] == null) _delivers[id] = timestamp;
    }

    @Override
    public String toString() {
        return "BroadcastService{" +
                "_broadcastId=" + _broadcastId +
                ", _delivered=" + _delivered +
                '}';
    }
}
