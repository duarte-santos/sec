package pt.tecnico.sec.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pt.tecnico.sec.client.report.LocationProof;

@SuppressWarnings("AccessStaticViaInstance")
@RestController
public class ClientController {

    private final ClientApplication _clientApp;

    @Autowired
    private ClientController(ClientApplication clientApp) {
        _clientApp = clientApp;
    }

    @GetMapping("/step/")
    public void step() {
        _clientApp.step();
        System.out.print("\n> ");
    }

    @GetMapping("/location-proof/{epoch}/{proverId}")
    public LocationProof locationProof(@PathVariable(value = "epoch") int proverEpoch, @PathVariable(value = "proverId") int proverId) throws Exception {
        return _clientApp.getUser().makeLocationProof(proverId, proverEpoch);
    }

}