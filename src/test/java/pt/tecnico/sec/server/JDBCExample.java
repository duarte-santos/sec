package pt.tecnico.sec.server;

import java.sql.*;

public class JDBCExample {
    // JDBC driver name and database URL
    private static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
    private static final String DB_URL = "jdbc:mysql://localhost/";

    //  Database credentials
    private static final String USER = "user";
    private static final String PASS = "pass";
    private static final String DATABASE_NAME = "sec";


    public void dropDatabase() {

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

            String sql = "DROP DATABASE " + DATABASE_NAME;
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


    public void createDatabase() {

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

            String sql = "CREATE DATABASE " + DATABASE_NAME;
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
