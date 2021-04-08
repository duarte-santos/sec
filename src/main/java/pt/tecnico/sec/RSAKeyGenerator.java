package pt.tecnico.sec;

import org.apache.tomcat.util.http.fileupload.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;

import static javax.xml.bind.DatatypeConverter.printHexBinary;

public class RSAKeyGenerator {

    public static final String KEYS_PATH = "src/main/resources/keys/";

    public static void main(String[] args) throws Exception {

        try {
            int userCount  = Integer.parseInt(args[0]);
            if (userCount <= 0)
                throw new NumberFormatException();

            FileUtils.cleanDirectory(new File(KEYS_PATH)); // clean key directory before generating new keys
            generateRSAKeys(userCount);
        }
        catch (NumberFormatException e) {
            System.out.println("Argument 'number of users' be a positive integer.");
            System.out.println("USAGE: ./mvnw spring-boot:run -Dspring-boot.run.arguments=\"[userCount]\" -Dstart-class=pt.tecnico.sec.RSAKeyGenerator");
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
        }

        System.out.println("Done.");
    }

    private static void generateRSAKeys(int userCount) throws GeneralSecurityException, IOException {
        for (int id = 0; id < userCount; id++) {
            write(KEYS_PATH + id);
        }

    }


    private static void write(String keyPath) throws GeneralSecurityException, IOException {
        // get an AES private key
        System.out.println("Generating RSA key ..." );
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(1024);
        KeyPair keys = keyGen.generateKeyPair();
        System.out.println("Finish generating RSA keys");

        System.out.println("Private Key:");
        PrivateKey privKey = keys.getPrivate();
        byte[] privKeyEncoded = privKey.getEncoded();
        System.out.println(printHexBinary(privKeyEncoded));
        System.out.println("Public Key:");
        PublicKey pubKey = keys.getPublic();
        byte[] pubKeyEncoded = pubKey.getEncoded();
        System.out.println(printHexBinary(pubKeyEncoded));

        System.out.println("Writing Private key to '" + keyPath + "' ..." );
        FileOutputStream privFos = new FileOutputStream(keyPath + ".priv");
        privFos.write(privKeyEncoded);
        privFos.close();
        System.out.println("Writing Pubic key to '" + keyPath + "' ..." );
        FileOutputStream pubFos = new FileOutputStream(keyPath + ".pub");
        pubFos.write(pubKeyEncoded);
        pubFos.close();
    }

    /*public static Key read(String keyPath) throws GeneralSecurityException, IOException {
        System.out.println("Reading key from file " + keyPath + " ...");
        FileInputStream fis = new FileInputStream(keyPath);
        byte[] encoded = new byte[fis.available()];
        fis.read(encoded);
        fis.close();

        return new SecretKeySpec(encoded, "RSA");
    }*/

}
