package pt.tecnico.sec.client;

import org.json.simple.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class EnvironmentGenerator {

    public static final String ENVIRONMENT_PATH = "src/main/resources/environment.json";

    public static void main(String[] args) throws IOException {

        try {
            int nX = Integer.parseInt(args[0]);
            int nY = Integer.parseInt(args[1]);
            int epochCount = Integer.parseInt(args[2]);
            int userCount  = Integer.parseInt(args[3]);
            if (nX <= 0 || nY <= 0 || epochCount <= 0 || userCount <= 0)
                throw new NumberFormatException();

            writeJSON(nX, nY, epochCount, userCount);
        }
        catch (NumberFormatException e) {
            System.out.println("All arguments must be positive integers.");
            System.out.println("USAGE: ./mvnw spring-boot:run -Dspring-boot.run.arguments=\"[nX] [nY] [epochCount] [userCount]\" -Dstart-class=pt.tecnico.sec.client.EnvironmentGenerator");
        }

    }

    public static void writeJSON(int nX, int nY, int epochCount, int userCount) throws IOException {
        JSONObject environmentJSON = new JSONObject();
        // generate one grid for each epoch
        for (int epoch = 0; epoch < epochCount; epoch++) {
            JSONObject gridJSON = new JSONObject();
            // generate one location for each user
            for (int userId = 0; userId < userCount; userId++) {
                // generate random location inside the grid limits
                Random random = new Random();
                int x = random.nextInt(nX); // FIXME : nX + 1 ?
                int y = random.nextInt(nY); // FIXME : nY + 1 ?
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
        try (FileWriter file = new FileWriter(ENVIRONMENT_PATH)) {
            file.write(environmentJSON.toJSONString());
            file.flush();
        }
    }

    public static void parseJSON() {
        // TODO
    }

}