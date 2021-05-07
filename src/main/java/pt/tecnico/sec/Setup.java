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

public class Setup {

    // JDBC driver name and database URL
    private static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String DB_URL = "jdbc:mysql://localhost/";

    //  Database credentials
    private static final String USER = "user";
    private static final String PASS = "pass";
    private static final String DATABASE_NAME = "sec";


    public static void main(String[] args) {

        try {
            final int nX = Integer.parseInt(args[0]);
            final int nY = Integer.parseInt(args[1]);
            final int epochCount = Integer.parseInt(args[2]);
            final int userCount  = Integer.parseInt(args[3]);
            final int serverCount  = Integer.parseInt(args[4]);
            if (nX <= 0 || nY <= 0 || epochCount <= 0 || userCount <= 0 || serverCount <= 0)
                throw new NumberFormatException();

            System.out.println("\n[CREATE DATABASES]");
            for (int serverId = 0; serverId < serverCount; serverId++) {
                createDatabase(serverId);
            }

            System.out.println("\n[CREATE ENVIRONMENT]");
            EnvironmentGenerator.main(Arrays.copyOfRange(args, 0, 4+1));
            
            System.out.println("\n[CREATE KEY_PAIRS]");
            RSAKeyGenerator.main(Arrays.copyOfRange(args, 3, 4+1));
        }
        catch (NumberFormatException e) {
            System.out.println("All arguments must be positive integers.");
            System.out.println("USAGE: ./mvnw spring-boot:run -Dspring-boot.run.arguments=\"[nX] [nY] [epochCount] [userCount] [serverCount]\" -Dstart-class=pt.tecnico.sec.Setup");
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
        }

    } // void main


    private static void createDatabase(int serverId) {

        final String databaseName = DATABASE_NAME + serverId;

        Connection conn = null;
        Statement  stmt = null;

        try {
            // Register JDBC driver
            Class.forName(JDBC_DRIVER);

            // Open a connection
            System.out.println("Connecting to a selected database...");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            System.out.println("Connected database successfully.");

            // Execute a query
            System.out.println("Creating database " + databaseName + "...");
            stmt = conn.createStatement();

            String sql = "CREATE DATABASE IF NOT EXISTS " + databaseName;
            stmt.executeUpdate(sql);
            System.out.println("Database created successfully.");
        }
        catch (SQLException se) {
            // Handle errors for JDBC
            se.printStackTrace();
        }
        catch (Exception e) {
            // Handle errors for Class.forName
            e.printStackTrace();
        }
        finally {
            // Finally block used to close resources
            try {
                if (stmt != null)
                    conn.close();
            }
            catch (SQLException se) {
                // Do nothing
            }

            try {
                if (conn != null)
                    conn.close();
            }
            catch (SQLException se) {
                se.printStackTrace();
            }
        }

    } // void createDatabase

} // class Setup
