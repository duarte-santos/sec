package pt.tecnico.sec;


import org.apache.tomcat.util.http.fileupload.FileUtils;
import sun.security.util.KnownOIDs;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.SubjectAlternativeNameExtension;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;
import sun.security.x509.CertificateExtensions;
import sun.security.x509.GeneralNames;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNameInterface;
import sun.security.x509.DNSName;
import sun.security.x509.IPAddressName;
import sun.security.util.DerOutputStream;

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
import java.util.Date;

import static pt.tecnico.sec.Constants.*;

public class Setup {

    // Constants
    private static final String USAGE = "./mvnw spring-boot:run -Dstart-class=pt.tecnico.sec.Setup -Dspring-boot.run.arguments=\"[nX] [nY] [epochCount] [userCount] [serverCount]\"";
    private static final String DN_NAME = "CN=test, OU=test, O=test, L=test, ST=test, C=CY";
    private static final String SHA256witchRSA = "SHA256withRSA";


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

            // FIXME : exchange with keystore
            System.out.println("\n[CREATE KEY_PAIRS]");
            RSAKeyGenerator.main(Arrays.copyOfRange(args, 3, 4+1));

            System.out.println("\n[CREATE KEY_PAIRS]");
            createKeyStores(userCount, serverCount, 1);

            System.out.println("\nDone!");

        }
        catch (NumberFormatException e) {
            System.out.println("All arguments must be positive integers.");
            System.out.println("USAGE: " + USAGE);
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

    private static void createKeyStores(int userCount, int serverCount, int haCount) throws NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException, SignatureException, NoSuchProviderException, InvalidKeyException {

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


    private static void generateAndStoreKeyPairs(String name, String password) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, NoSuchProviderException, InvalidKeyException, SignatureException {
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
     * @author https://www.baeldung.com/java-keystore
     */
    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws CertificateException, IOException, NoSuchProviderException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        X509CertInfo certInfo = new X509CertInfo();
        // Serial number and version
        certInfo.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(new BigInteger(64, new SecureRandom())));
        certInfo.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));

        // Subject & Issuer
        X500Name owner = new X500Name(DN_NAME);
        certInfo.set(X509CertInfo.SUBJECT, owner);
        certInfo.set(X509CertInfo.ISSUER, owner);

        // Key and algorithm
        certInfo.set(X509CertInfo.KEY, new CertificateX509Key(keyPair.getPublic()));
        AlgorithmId algorithm = new AlgorithmId(ObjectIdentifier.of(KnownOIDs.SHA256withRSA));
        certInfo.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algorithm));

        // Validity
        Date validFrom = new Date();
        Date validTo = new Date(validFrom.getTime() + 50L * 365L * 24L * 60L * 60L * 1000L); //50 years
        CertificateValidity validity = new CertificateValidity(validFrom, validTo);
        certInfo.set(X509CertInfo.VALIDITY, validity);

        GeneralNameInterface dnsName = new DNSName("baeldung.com");
        DerOutputStream dnsNameOutputStream = new DerOutputStream();
        dnsName.encode(dnsNameOutputStream);

        GeneralNameInterface ipAddress = new IPAddressName("127.0.0.1");
        DerOutputStream ipAddressOutputStream = new DerOutputStream();
        ipAddress.encode(ipAddressOutputStream);

        GeneralNames generalNames = new GeneralNames();
        generalNames.add(new GeneralName(dnsName));
        generalNames.add(new GeneralName(ipAddress));

        CertificateExtensions ext = new CertificateExtensions();
        ext.set(SubjectAlternativeNameExtension.NAME, new SubjectAlternativeNameExtension(generalNames));

        certInfo.set(X509CertInfo.EXTENSIONS, ext);

        // Create certificate and sign it
        X509CertImpl cert = new X509CertImpl(certInfo);
        cert.sign(keyPair.getPrivate(), SHA256witchRSA);

        // Since the SHA1withRSA provider may have a different algorithm ID to what we think it should be,
        // we need to reset the algorithm ID, and resign the certificate
        AlgorithmId actualAlgorithm = (AlgorithmId) cert.get(X509CertImpl.SIG_ALG);
        certInfo.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, actualAlgorithm);
        X509CertImpl newCert = new X509CertImpl(certInfo);
        newCert.sign(keyPair.getPrivate(), SHA256witchRSA);

        return newCert;

    } // X509Certificate generateSelfSignedCertificate


} // class Setup
