package pt.tecnico.sec.server;

import pt.tecnico.sec.ObjectMapperHandler;
import pt.tecnico.sec.client.ObtainLocationRequest;

import java.util.HashMap;
import java.util.Map;

import static pt.tecnico.sec.Constants.FAULTS;

public class BroadcastServiceR {
    private final ServerApplication _serverApp;
    private final BroadcastId _broadcastId;
    private final int _serverCount;

    private boolean _sentEcho = false;
    private boolean _sentReady = false;
    private boolean _delivered = false;
    private final BroadcastRead[] _echos;
    private final BroadcastRead[] _readys;

    Map<Integer, DBLocationReport> _delivers;
    ObtainLocationRequest _request;

    BroadcastServiceR(ServerApplication serverApp, BroadcastId broadcastId) {
        _serverApp = serverApp;
        _broadcastId = broadcastId;
        _serverCount = _serverApp.getServerCount();
        _echos = new BroadcastRead[_serverCount];
        _readys = new BroadcastRead[_serverCount];
        _delivers = new HashMap<>(_serverCount);
    }

    /* ========================================================== */
    /* ====[              Auxiliary functions               ]==== */
    /* ========================================================== */

    public boolean is_delivered() {
        return _delivered;
    }

    public BroadcastRead searchForMajorityMessage(BroadcastRead[] messages, int quorum) {
        // count the times a message appears
        Map<BroadcastRead, Integer> counter = new HashMap<>();
        for (BroadcastRead m : messages) {
            if (m == null) continue;
            Integer count = counter.get(m);
            if (count == null)
                counter.put(m, 1);
            else
                counter.replace(m, count+1);
        }
        // search for message that appears more than <quorum> times
        for (BroadcastRead m : counter.keySet()) {
            if (counter.get(m) > quorum) return m;
        }
        return null;
    }

    public boolean validResponse(DBLocationReport report) {
        if (report == null) return true; //FIXME
        return report.get_userId() == _request.get_userId() && report.get_epoch() == _request.get_epoch();
    }

    /* ========================================================== */
    /* ====[              Double Echo Broadcast             ]==== */
    /* ========================================================== */

    // Send SEND
    public DBLocationReport broadcastSend(ObtainLocationRequest request) throws Exception {
        _delivers = new HashMap<>(_serverCount);
        _request = request;

        BroadcastRead br = new BroadcastRead(_broadcastId, request);
        byte[] m = ObjectMapperHandler.writeValueAsBytes(br);

        // Start threads to broadcast
        System.out.println("Broadcasting read...");
        _serverApp.postToServers(m, "/broadcast-send-r");
        while (_delivers.size() <= (_serverCount + FAULTS) / 2) {
            // empty
        }

        // Choose the report with the largest timestamp
        DBLocationReport finalLocationReport = null;
        for (DBLocationReport locationReport : _delivers.values()) {
            if (locationReport == null) continue;
            if (finalLocationReport == null || locationReport.get_timestamp() > finalLocationReport.get_timestamp())
                finalLocationReport = locationReport;
        }

        return finalLocationReport;
    }

    // Deliver SEND -> Send ECHO
    public void broadcastSENDDeliver(BroadcastRead br) throws Exception {
        if (!_sentEcho) {
            byte[] m = ObjectMapperHandler.writeValueAsBytes(br);
            _sentEcho = true;
            _serverApp.postToServers(m, "/broadcast-echo-r");
        }
    }

    // Deliver ECHO -> Send READY
    public void broadcastECHODeliver(int id, BroadcastRead br) throws Exception {
        if (_echos[id] == null) _echos[id] = br;

        if (!_sentReady) {
            BroadcastRead message = searchForMajorityMessage(_echos, (_serverCount+FAULTS)/2);
            if (message != null) {
                _sentReady = true;
                byte[] m = ObjectMapperHandler.writeValueAsBytes(message);
                _serverApp.postToServers(m, "/broadcast-ready-r");
            }
        }
    }

    // Deliver READY -> Send READY, Deliver response
    public boolean broadcastREADYDeliver(int id, BroadcastRead br) throws Exception {
        if (_readys[id] == null) _readys[id] = br;
        if (!_sentReady) {
            BroadcastRead message = searchForMajorityMessage(_readys, FAULTS); // FIXME : f is faults or byzantines?
            if (message != null) {
                _sentReady = true;
                byte[] m = ObjectMapperHandler.writeValueAsBytes(message);
                _serverApp.postToServers(m, "/broadcast-ready-r");
            }
            else return false; // for efficiency
        }
        if (!_delivered) {
            BroadcastRead message = searchForMajorityMessage(_readys, 2*FAULTS); // FIXME : f is faults or byzantines?
            if (message != null) {
                _delivered = true;
                return true;
            }
        }
        return false;
    }

    // Received answer
    public void broadcastDeliver(int id, DBLocationReport report) {
        if (!_delivers.containsKey(id)) _delivers.put(id, report);
    }

    @Override
    public String toString() {
        return "BroadcastServiceR{" +
                "_broadcastId=" + _broadcastId +
                ", _delivered=" + _delivered +
                '}';
    }

}
