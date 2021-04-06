package pt.tecnico.sec.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pt.tecnico.sec.server.LocationReport;

import javax.servlet.http.HttpServletRequest;

@RestController
public class ClientController {

    private final User _user;

    @Autowired
    private ClientController(ClientApplication clientApp) {
        _user = clientApp.getUser();
    }

    @GetMapping("/step/")
    public void step(HttpServletRequest request) {
        System.out.println("\r  \n[Request received] Type: Step, From: " + request.getRemoteAddr() + ":" + request.getRemotePort());
        _user.step();
        System.out.print("\n> ");
    }

    @GetMapping("/location-proof/{epoch}/{proverId}")
    public LocationProof locationProof(@PathVariable(value = "epoch") int proverEpoch, @PathVariable(value = "proverId") int proverId) {
        if (proverEpoch > _user.getEpoch()) // do not accept requests regarding the future
            throw new IllegalArgumentException("Cannot prove location requests regarding future epochs");

        int witnessId = _user.getId();
        Location witnessLoc = _user.getEpochLocation(proverEpoch);
        String type = _user.isNearby(proverEpoch, proverId) ? "success" : "failure";

        Value value = new Value(witnessLoc, proverId, witnessId);

        System.out.println("\r[Request received] Type: LocationProof, From: " + proverId + ", Epoch: " + proverEpoch + ", Result: " + type);
        return new LocationProof(type, value);
    }

    @PostMapping("/location-report")
    public void reportLocation(@RequestBody LocationReport report){

        System.out.println(report);
        // FIXME Check stuff

        /*reportRepository.save(report);
        for (LocationReport r : reportRepository.findAll() ){
            System.out.println(r.toString());
        }*/
    }
}

