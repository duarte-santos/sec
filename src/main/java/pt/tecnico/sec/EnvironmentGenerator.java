package pt.tecnico.sec;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import pt.tecnico.sec.client.Environment;
import pt.tecnico.sec.client.Grid;
import pt.tecnico.sec.client.Location;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class EnvironmentGenerator {

    public static final String ENVIRONMENT_PATH = "src/main/resources/environment.json";

    public static void main(String[] args) {
        try {
            int nX = Integer.parseInt(args[0]);
            int nY = Integer.parseInt(args[1]);
            int epochCount = Integer.parseInt(args[2]);
            int userCount  = Integer.parseInt(args[3]);
            if (nX <= 0 || nY <= 0 || epochCount <= 0 || userCount <= 0)
                throw new NumberFormatException();

            writeEnvironmentJSON(nX, nY, epochCount, userCount);
        }
        catch (NumberFormatException e) {
            System.out.println("All arguments must be positive integers.");
            System.out.println("USAGE: ./mvnw spring-boot:run -Dspring-boot.run.arguments=\"[nX] [nY] [epochCount] [userCount]\" -Dstart-class=pt.tecnico.sec.EnvironmentGenerator");
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    // { epoch "0": { user "0": { "x": int , "y": int }, user "1": { "x": int , "y": int } }, epoch "1": { ... }, ... }

    @SuppressWarnings("unchecked")
    public static void writeEnvironmentJSON(int nX, int nY, int epochCount, int userCount) throws IOException {
        JSONObject environmentJSON = new JSONObject();
        // generate one grid for each epoch
        for (int epoch = 0; epoch < epochCount; epoch++) {
            JSONObject gridJSON = new JSONObject();
            // generate one location for each user
            for (int userId = 0; userId < userCount; userId++) {
                // generate random location inside the grid limits
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
        try (FileWriter file = new FileWriter(ENVIRONMENT_PATH)) {
            file.write(environmentJSON.toJSONString());
            file.flush();
        }
    }

    public static Environment parseEnvironmentJSON() throws IOException, ParseException {
        Environment environment = new Environment();
        JSONParser jsonParser = new JSONParser();
        try (FileReader reader = new FileReader(ENVIRONMENT_PATH)) {
            // access all epochs
            JSONObject epochSet = (JSONObject) jsonParser.parse(reader);
            for (Object epochStringObject : epochSet.keySet()) {
                String epochString = (String) epochStringObject;
                Grid grid = new Grid();
                // access all users
                JSONObject userSet = (JSONObject) epochSet.get(epochString);
                for (Object userStringObject : userSet.keySet()) {
                    String userString = (String) userStringObject;
                    // access specific user
                    JSONObject userObject = (JSONObject) userSet.get(userString);
                    // fetch location
                    int x = ( (Long) userObject.get("x") ).intValue();
                    int y = ( (Long) userObject.get("y") ).intValue();

                    // append user location to grid
                    int user = Integer.parseInt(userString);
                    Location location = new Location(x, y);
                    grid.addUserLocation(user, location);
                }
                // append grind (of epoch) to environment
                int epoch = Integer.parseInt(epochString);
                environment.addEpochGrid(epoch, grid);
            }
        }
        return environment;
    }

}