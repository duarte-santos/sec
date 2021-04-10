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
    public SignedLocationProof locationProof(@PathVariable(value = "epoch") int proverEpoch, @PathVariable(value = "proverId") int proverId) throws Exception {
        SignedLocationProof proof = _clientApp.getUser().makeLocationProof(proverId, proverEpoch);
        System.out.print("\r[Request received] Type: LocationProof, From: " + proverId + ", Epoch: " + proverEpoch + ", Result: " + proof.get_locationProof().get_type() + "\n> ");
        return proof;
    }

}