package pt.tecnico.sec.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
public class ClientController {

    private final ClientApplication _clientApp;

    @Autowired
    private ClientController(ClientApplication clientApp) {
        _clientApp = clientApp;
    }

    @GetMapping("/step/")
    public void step(HttpServletRequest request) {
        System.out.println("\r  \n[Request received] Type: Step, From: " + request.getRemoteAddr() + ":" + request.getRemotePort());
        _clientApp.step();
        System.out.print("\n> ");
    }

    @GetMapping("/location-proof/{epoch}/{proverId}")
    public LocationProof locationProof(@PathVariable(value = "epoch") int proverEpoch, @PathVariable(value = "proverId") int proverId) {
        User witness = _clientApp.getUser();
        int currentEpoch = witness.getEpoch();
        int witnessId = witness.getId();

        Location witnessLoc;
        String type;
        if (proverEpoch == currentEpoch) { // users are synchronized
            witnessLoc = witness.getLocation();
            type = witness.isNearby(proverId) ? "success" : "failure";
        }
        else if (proverEpoch == currentEpoch - 1) { // prover is not synchronized yet
            witnessLoc = witness.getPrevLocation();
            type = witness.wasNearby(proverId) ? "success" : "failure";
        }
        else
            throw new IllegalArgumentException("Can only prove location requests regarding current or previous epoch");

        Value value = new Value(witnessLoc, proverId, witnessId);
        System.out.print("\r[Request received] Type: LocationProof, From: " + proverId + ", Epoch: " + proverEpoch + ", Result: " + type + "\n> ");
        return new LocationProof(type, value);
    }
}

