package pt.tecnico.sec;

import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultAlgorithmNameFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import static pt.tecnico.sec.Constants.*;

public class Setup {

    public static void main(String[] args) throws Exception {

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

            System.out.println("\n[CREATE KEY_STORES]");
            createKeyStores(userCount, serverCount, 1);

            System.out.println("\nDone!\n");

        }
        catch (NumberFormatException e) {
            System.out.println("All arguments must be positive integers.");
            System.out.println("USAGE: ./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.Setup -Dspring-boot.run.arguments=\"[nX] [nY] [epochCount] [userCount] [serverCount]\"");
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
        }

    } // void main


    /* ========================================================== */
    /* ====[                    Database                    ]==== */
    /* ========================================================== */

    private static void createDatabase(int serverId) {

        String databaseName = DATABASE_NAME + serverId;

        Connection conn = null;
        Statement stmt = null;

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


    /* ========================================================== */
    /* ====[                    KeyStore                    ]==== */
    /* ========================================================== */

    private static void createKeyStores(int userCount, int serverCount, int haCount) throws NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, OperatorCreationException {

        // Create a KeyStore directory if it doesnt already exist
        File directory = new File(KEYSTORE_DIRECTORY);
        if (!directory.exists()) {
            directory.mkdir();
        }

        // Clean the directory before generating new KeyStores
        FileUtils.cleanDirectory(directory);

        // Generate and store a KeyPair for each User
        for (int userId = 0; userId < userCount; userId++) {
            String name = "user" + userId;
            String password = "user" + userId;
            generateAndStoreKeyPairs(name, password);
        }

        // Generate and store a KeyPair for each Server
        for (int serverId = 0; serverId < serverCount; serverId++) {
            String name = "server" + serverId;
            String password = "server" + serverId;
            generateAndStoreKeyPairs(name, password);
        }

        // Generate and store a KeyPair for each HealthAuthority
        for (int haId = 0; haId < haCount; haId++) {
            String name = "ha" + haId;
            String password = "ha" + haId;
            generateAndStoreKeyPairs(name, password);
        }

    } // void createKeyStores


    private static void generateAndStoreKeyPairs(String name, String password) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, OperatorCreationException {
        // Crate a KeyStore instance
        JavaKeyStore keyStore = new JavaKeyStore("PKCS12", name, password);
        keyStore.createEmptyKeyStore();
        keyStore.loadKeyStore();

        // Generate the key pair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Generate a self signed certificate
        X509Certificate certificate = generateSelfSignedCertificate(keyPair);

        // Store the certificate (public key)
        keyStore.setCertificateEntry("Certificate", certificate);

        // Store the private key
        X509Certificate[] certificateChain = new X509Certificate[1];
        certificateChain[0] = certificate;
        keyStore.setKeyEntry("PrivateKey", keyPair.getPrivate(), password, certificateChain);

    } // void generateAndStoreKeyPair


    /**
     * This function was adapted from:
     * @author https://stackoverflow.com/a/43918337
     */
    public static X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws OperatorCreationException, CertificateException, IOException {
        Provider bcProvider = new BouncyCastleProvider();
        Security.addProvider(bcProvider);

        long now = System.currentTimeMillis();
        Date startDate = new Date(now);

        X500Name dnName = new X500Name("CN=test, OU=test, O=test, L=test, ST=test, C=CY");

        BigInteger certSerialNumber = new BigInteger(Long.toString(now)); // Using the current timestamp as the certificate serial number

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        calendar.add(Calendar.YEAR, 1); // Valid for 1 year
        Date endDate = calendar.getTime();

        String signatureAlgorithm = "SHA256WithRSA"; // Use appropriate signature algorithm based on your KeyPair algorithm
        ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate());

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(dnName, certSerialNumber, startDate, endDate, dnName, keyPair.getPublic());

        // Extensions: Basic Constraints
        BasicConstraints basicConstraints = new BasicConstraints(true); // true for CA, false for EndEntity
        ASN1ObjectIdentifier oid = org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.sha256WithRSAEncryption;
        certBuilder.addExtension(oid, true, basicConstraints); // Basic Constraints is usually marked as critical

        return new JcaX509CertificateConverter().setProvider(bcProvider).getCertificate(certBuilder.build(contentSigner));

    } // X509Certificate generateSelfSignedCertificate;


} // class Setup
