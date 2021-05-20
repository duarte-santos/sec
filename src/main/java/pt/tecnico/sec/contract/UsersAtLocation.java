package pt.tecnico.sec.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.sec.client.report.Location;
import pt.tecnico.sec.client.report.LocationReport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsersAtLocation {
    private Location _location;
    private int _epoch;
    private List<SignedLocationReport> _reports = new ArrayList<>();

    public UsersAtLocation() {}

    public UsersAtLocation(Location location, int epoch, List<SignedLocationReport> reports) {
        this._location = location;
        this._epoch = epoch;
        this._reports = reports;
    }

    // convert from bytes
    public static UsersAtLocation getFromBytes(byte[] userListBytes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(userListBytes, UsersAtLocation.class);
    }

    public Location get_location() {
        return _location;
    }

    public void set_location(Location location) {
        this._location = location;
    }

    public int get_epoch() {
        return _epoch;
    }

    public void set_epoch(int epoch) {
        this._epoch = epoch;
    }

    public List<SignedLocationReport> get_reports() {
        return _reports;
    }

    public void set_reports(List<SignedLocationReport> _reports) {
        this._reports = _reports;
    }

    public Set<Integer> retrieveUserIds() {
        Set<Integer> userIds = new HashSet<>();
        for (SignedLocationReport report : _reports)
            userIds.add(report.get_userId());
        return userIds;
    }

    public List<LocationReport> retrieveLocationReports() {
        List<LocationReport> locationReports = new ArrayList<>();
        for (SignedLocationReport signedReport : _reports)
            locationReports.add(signedReport.get_report());
        return locationReports;
    }

    @Override
    public String toString() {
        if (_reports.size() == 0) return "No users at location " + _location + " and epoch " + _epoch;
        else return "Users at location " + _location + " and epoch " + _epoch + ": " + retrieveUserIds()
                + "\nReports: " + retrieveLocationReports();
    }
}
