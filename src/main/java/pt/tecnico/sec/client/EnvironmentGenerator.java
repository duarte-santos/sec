package pt.tecnico.sec.client;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

public class EnvironmentGenerator {

    public static void main(String[] args) throws IOException {

        // FIXME : receive and parse/validate args
        String filename = "environment.json"; // FIXME : filepath
        int nX = 10;
        int nY = 10;
        int epochCount = 10;
        int userCount = 3;

        JSONObject environmentJSON = new JSONObject();
        for (int epoch = 0; epoch < epochCount; epoch++) {
            JSONObject gridJSON = new JSONObject();
            for (int userId = 0; userId < userCount; userId++) {
                // generate location inside the grid limits
                Random random = new Random();
                int x = random.nextInt(nX);
                int y = random.nextInt(nY);
                // add location to user
                JSONObject userJSON = new JSONObject();
                userJSON.put("x", x);
                userJSON.put("y", y);
                // add user to grid (of epoch)
                gridJSON.put(String.valueOf(userId), userJSON);
            }
            // add grid (of epoch) to environment
            environmentJSON.put(String.valueOf(epoch), gridJSON);
        }
        // write environment to a JSON file
        Files.write(Paths.get(filename), environmentJSON.toString().getBytes());
    }

}
