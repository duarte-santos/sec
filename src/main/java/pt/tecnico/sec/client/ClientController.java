package pt.tecnico.sec.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientController {

    private final ClientApplication _clientApp; //FIXME hooooow

    @Autowired
    private ClientController(ClientApplication clientApp) {
        _clientApp = clientApp;
    }

    @GetMapping("/location-proof/{proverId}")
    public LocationProof locationProof(@PathVariable(value = "proverId") int proverId) {
        int witnessId = _clientApp.getUser().getId();

        String type = (_clientApp.getUser().getGrid().isNearby(witnessId, proverId)) ? "success" : "failure";
        Value value = new Value(_clientApp.getUser().getLocation(), proverId, witnessId);

        System.out.print("\r\nSent proof to user " + proverId + " with value \"" + type + "\"\n\n> ");

        return new LocationProof(type, value);
    }
}

