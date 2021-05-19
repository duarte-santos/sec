package pt.tecnico.sec;

import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
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

            // Add BouncyCastle provider
            BouncyCastleProvider bcProvider = new BouncyCastleProvider();
            Security.addProvider(bcProvider);

            // Setup necessary assets

            System.out.println("\n[CREATE DATABASES]");
            createDatabases(serverCount);

            System.out.println("\n[CREATE ENVIRONMENT]");
            EnvironmentGenerator.main(Arrays.copyOfRange(args, 0, 4+1));

            System.out.println("\n[CREATE KEY_STORES]");
            createKeyStores(userCount, serverCount, 1, bcProvider);

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

    private static void createDatabases(int serverCount) {
        for (int serverId = 0; serverId < serverCount; serverId++) {
            createDatabase(serverId);
        }
    }

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
        catch (Exception e) {
            // SQLException: Handle errors for JDBC
            // Exception: Handle errors for Class.forName
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

    @SuppressWarnings({"SameParameterValue", "ResultOfMethodCallIgnored"})
    private static void createCleanDirectory(String path) throws IOException {
        // Create directory if it doesnt already exist
        File directory = new File(path);
        if (!directory.exists()) {
            directory.mkdir();
        }
        // Clean the directory
        FileUtils.cleanDirectory(directory);
    }

    private static class CertificateEntry {
        private final String _name;
        private final String _password;
        private final X509Certificate _certificate;
        public CertificateEntry(String name, String password, X509Certificate certificate) {
            _name = name;
            _password = password;
            _certificate = certificate;
        }
        public String getName() { return _name; }
        public String getPassword() { return _password; }
        public X509Certificate getCertificate() { return _certificate; }
    }

    @SuppressWarnings("SameParameterValue")
    private static void createKeyStores(int userCount, int serverCount, int haCount, BouncyCastleProvider bcProvider) throws NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, OperatorCreationException {
        createCleanDirectory(KEYSTORE_DIRECTORY);

        CertificateEntry[] certificatesArray = new CertificateEntry[userCount + serverCount + haCount];
        int entry = 0;

        // Generate and store a KeyPair for each User
        for (int userId = 0; userId < userCount; userId++) {
            String name = "user" + userId;
            String password = "user" + userId;
            X509Certificate certificate = generateAndStoreKeyPairs(name, password, bcProvider);
            certificatesArray[entry++] = new CertificateEntry(name, password, certificate);
        }

        // Generate and store a KeyPair for each Server
        for (int serverId = 0; serverId < serverCount; serverId++) {
            String name = "server" + serverId;
            String password = "server" + serverId;
            X509Certificate certificate = generateAndStoreKeyPairs(name, password, bcProvider);
            certificatesArray[entry++] = new CertificateEntry(name, password, certificate);
        }

        // Generate and store a KeyPair for each HealthAuthority
        for (int haId = 0; haId < haCount; haId++) {
            String name = "ha" + haId;
            String password = "ha" + haId;
            X509Certificate certificate = generateAndStoreKeyPairs(name, password, bcProvider);
            certificatesArray[entry++] = new CertificateEntry(name, password, certificate);
        }

        // Exchange certificates between all entities
        for (int iEntity = 0; iEntity < certificatesArray.length; iEntity++) {
            // Load the entity's keystore credentials
            String keyStoreName = certificatesArray[iEntity].getName();
            String keyStorePassword = certificatesArray[iEntity].getPassword();
            // Load the entity's keystore
            JavaKeyStore keyStore = new JavaKeyStore(KEYSTORE_TYPE, keyStorePassword, keyStoreName + KEYSTORE_EXTENSION);
            keyStore.loadKeyStore();
            for (int iCertificate = 0; iCertificate < certificatesArray.length; iCertificate++) {
                // Skip the certificate of the current entity (user, server or ha)
                if (iCertificate == iEntity) continue;
                // Load the certificate information
                String name = certificatesArray[iCertificate].getName();
                X509Certificate certificate = certificatesArray[iCertificate].getCertificate();
                // Add certificate to the entity's keystore
                keyStore.setCertificateEntry(name, certificate);
            }
            // Save the KeyStore state
            keyStore.storeKeyStore();
        }

    } // void createKeyStores


    private static X509Certificate generateAndStoreKeyPairs(String name, String password, BouncyCastleProvider bcProvider) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, OperatorCreationException {
        System.out.println("Name: " + name + ", \tPassword: " + password);

        // Create a KeyStore instance
        JavaKeyStore keyStore = new JavaKeyStore(KEYSTORE_TYPE, password, name + KEYSTORE_EXTENSION);
        keyStore.createEmptyKeyStore();
        keyStore.loadKeyStore();

        // Generate the key pair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", bcProvider);
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Generate a self signed certificate
        X509Certificate certificate = generateSelfSignedCertificate(keyPair, bcProvider);

        // Store the certificate (public key)
        keyStore.setCertificateEntry(KEYSTORE_CERTIFICATE, certificate);

        // Store the private key
        X509Certificate[] certificateChain = new X509Certificate[1];
        certificateChain[0] = certificate;
        keyStore.setKeyEntry(KEYSTORE_PRIVATE_KEY, keyPair.getPrivate(), password, certificateChain);

        // Save the KeyStore state
        keyStore.storeKeyStore();

        return certificate;

    } // void generateAndStoreKeyPair


    /**
     * This function was adapted from:
     * @author https://stackoverflow.com/a/43918337
     */
    public static X509Certificate generateSelfSignedCertificate(KeyPair keyPair, BouncyCastleProvider bcProvider) throws OperatorCreationException, CertificateException, IOException {
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
