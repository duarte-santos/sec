package pt.tecnico.sec;

public final class Constants {
    // Environment
    public static final String ENVIRONMENT_PATH = "src/main/resources/environment.json";
    public static final String KEYS_PATH = "src/main/resources/keys/";

    // KeyStore
    public static final String KEYSTORE_DIRECTORY = "src/main/resources/keystore/";
    public static final String KEYSTORE_EXTENSION = ".pfx"; // .pfx or .p12 for "PKCS#12" // .jks for "JKS"
    public static final String KEYSTORE_TYPE = "PKCS12";
    public static final String PRIVATE_KEY = "private";
    public static final String CERTIFICATE = "certificate";

    // JDBC driver name and database URL
    public static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    public static final String DB_URL = "jdbc:mysql://localhost/";

    // Database credentials
    public static final String USER = "user";
    public static final String PASS = "pass";
    public static final String DATABASE_NAME = "sec";

    // General
    public static final int DETECTION_RANGE = 2;
    public static final int SECRET_KEY_DURATION = 2;
    public static final int BYZANTINE_USERS  = 1;
    public static final int FAULTS = 0; //FIXME
    public static final String SUCCESS = "success";
    public static final String FAILURE = "failure";
    public static final String OK = "OK";

    // Health Authority
    public static final int HA_BASE_PORT = 6000;
    public static final String HA_USAGE = "Usage: ./mvnw spring-boot:run -Dspring-boot.run.arguments=\"[serverCount]\" -\"Dstart-class=pt.tecnico.sec.healthauthority.HealthAuthorityApplication";

    // Client
    public static final int CLIENT_BASE_PORT = 8000;
    public static final String CLIENT_USAGE = "Usage: ./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.client.ClientApplication -Dspring-boot.run.arguments=\"[userId serverCount]\"";
    public static final String EXCEPTION_STR = "Caught exception with description: ";
    public static final String EXIT_CMD = "exit";
    public static final String HELP_CMD = "help";
    public static final String STEP_CMD = "step";
    public static final String SUBMIT_CMD = "submit";
    public static final String OBTAIN_CMD = "obtain";
    public static final String PROOFS_CMD = "proofs";

    // Server
    public static final int SERVER_BASE_PORT = 9000;
    public static final String SERVER_USAGE = "Usage: ./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.server.ServerApplication -Dspring-boot.run.arguments=\"[serverId] [serverCount] [userCount]\"";

}
