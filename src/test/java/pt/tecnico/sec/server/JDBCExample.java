package pt.tecnico.sec.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static pt.tecnico.sec.Constants.DATABASE_NAME;

public class JDBCExample {
    // JDBC driver name and database URL
    private static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String DB_URL = "jdbc:mysql://localhost/";

    //  Database credentials
    private static final String USER = "user";
    private static final String PASS = "pass";
    private static final String DATABASE_NAME = "sec";


    public static void dropDatabases(int serverCount) {
        for (int serverId = 0; serverId < serverCount; serverId++)
            dropDatabase(serverId);
    }

    public static void createDatabases(int serverCount) {
        for (int serverId = 0; serverId < serverCount; serverId++)
            createDatabase(serverId);
    }


    public static void dropDatabase(int serverId) {

        String databaseName = DATABASE_NAME + serverId;

        Connection conn = null;
        Statement stmt = null;

        try{
            // Register JDBC driver
            Class.forName(JDBC_DRIVER);

            // Open a connection
            System.out.println("Connecting to a selected database...");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            System.out.println("Connected database successfully...");

            // Execute a query
            System.out.println("Deleting database...");
            stmt = conn.createStatement();

            String sql = "DROP DATABASE IF EXISTS " + databaseName;
            stmt.executeUpdate(sql);
            System.out.println("Database deleted successfully...");
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
            // finally block used to close resources
            try {
                if (stmt != null)
                    conn.close();
            }
            catch (SQLException se) {
                // do nothing
            }

            try {
                if (conn != null)
                    conn.close();
            }
            catch (SQLException se) {
                se.printStackTrace();
            }
        }

        System.out.println("Goodbye!");
    }


    public static void createDatabase(int serverId) {

        String databaseName = DATABASE_NAME + serverId;

        Connection conn = null;
        Statement stmt = null;

        try{
            // Register JDBC driver
            Class.forName(JDBC_DRIVER);

            // Open a connection
            System.out.println("Connecting to a selected database...");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            System.out.println("Connected database successfully...");

            // Execute a query
            System.out.println("Creating database...");
            stmt = conn.createStatement();

            String sql = "CREATE DATABASE IF NOT EXISTS " + databaseName;
            stmt.executeUpdate(sql);
            System.out.println("Database created successfully...");
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
            // finally block used to close resources
            try {
                if (stmt != null)
                    conn.close();
            }
            catch (SQLException se) {
                // do nothing
            }

            try {
                if (conn != null)
                    conn.close();
            }
            catch (SQLException se) {
                se.printStackTrace();
            }
        }

        System.out.println("Goodbye!");
    }

}
