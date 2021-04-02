package pt.tecnico.sec.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientController {

    User _user; //FIXME hooooow

    @Autowired
    private ClientController(User user) {
        _user = user;
    }

    @GetMapping("/location-proof")
    public LocationProof locationProof(@RequestParam(value = "proverId") int proverId, @RequestParam(value = "location") Location location) {
        int witnessId = _user.getId();

        String type = (_user.getGrid().isNearby(witnessId, proverId)) ? "success" : "failure";
        Value value = new Value(location, proverId, witnessId);

        return new LocationProof(type, value);
    }


}

