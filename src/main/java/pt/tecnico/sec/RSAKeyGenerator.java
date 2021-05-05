package pt.tecnico.sec;

import org.apache.tomcat.util.http.fileupload.FileUtils;

import javax.crypto.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static javax.xml.bind.DatatypeConverter.printHexBinary;

public class RSAKeyGenerator {

    public static final String KEYS_PATH = "src/main/resources/keys/";

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void main(String[] args) throws Exception {

        try {
            int userCount  = Integer.parseInt(args[0]);
            if (userCount <= 0)
                throw new NumberFormatException();

            writeKeyPairs(userCount);
        }
        catch (NumberFormatException e) {
            System.out.println("Argument 'number of users' be a positive integer.");
            System.out.println("USAGE: ./mvnw spring-boot:run -Dspring-boot.run.arguments=\"[userCount]\" -Dstart-class=pt.tecnico.sec.RSAKeyGenerator");
        }
    }

    public static void writeKeyPairs(int userCount) throws Exception {
        try {
            // create key directory if it doesnt exist already
            File directory = new File(KEYS_PATH);
            if (!directory.exists()) {
                directory.mkdir();
            }

            // clean key directory before generating new keys
            FileUtils.cleanDirectory(new File(KEYS_PATH));
            for (int id = 0; id < userCount; id++) {
                writeKeyPair(KEYS_PATH + id);
            }
            writeKeyPair(KEYS_PATH + "server");
            writeKeyPair(KEYS_PATH + "ha");
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
        }

        System.out.println("Done.");
    }


    /* ========================================================== */
    /* ====[                 Manage KeyPair                 ]==== */
    /* ========================================================== */

    private static void writeKeyPair(String keyPath) throws GeneralSecurityException, IOException {
        // get an AES private key
        System.out.println("Generating RSA key ..." );
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
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

    public static KeyPair readKeyPair(String publicKeyPath, String privateKeyPath) throws GeneralSecurityException, IOException {
        PublicKey pub = readPublicKey(publicKeyPath);
        PrivateKey priv = readPrivateKey(privateKeyPath);
        return new KeyPair(pub, priv);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static PublicKey readPublicKey(String publicKeyPath) throws GeneralSecurityException, IOException {
        System.out.println("Reading public key from file " + publicKeyPath + " ...");
        FileInputStream pubFis = new FileInputStream(publicKeyPath);
        byte[] pubEncoded = new byte[pubFis.available()];
        pubFis.read(pubEncoded);
        pubFis.close();

        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubEncoded);
        KeyFactory keyFacPub = KeyFactory.getInstance("RSA");
        return keyFacPub.generatePublic(pubSpec);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static PrivateKey readPrivateKey(String privateKeyPath) throws GeneralSecurityException, IOException {
        System.out.println("Reading private key from file " + privateKeyPath + " ...");
        FileInputStream privFis = new FileInputStream(privateKeyPath);
        byte[] privEncoded = new byte[privFis.available()];
        privFis.read(privEncoded);
        privFis.close();

        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privEncoded);
        KeyFactory keyFacPriv = KeyFactory.getInstance("RSA");
        return keyFacPriv.generatePrivate(privSpec);
    }


    /* ========================================================== */
    /* ====[                Encrypt/Decrypt                 ]==== */
    /* ========================================================== */

    public static String encrypt(byte[] data, PublicKey key) throws BadPaddingException, IllegalBlockSizeException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] cipherText = cipher.doFinal(data);
        return Base64.getEncoder().encodeToString(cipherText);
    }

    public static byte[] decrypt(String cipherText, PrivateKey key) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        byte[] bytes = Base64.getDecoder().decode(cipherText);
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(bytes);
    }

    public static String encryptSecretKey(SecretKey secretKey, PublicKey key) throws BadPaddingException, IllegalBlockSizeException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        byte[] encodedKey = secretKey.getEncoded();
        return encrypt(encodedKey, key);
    }

    public static SecretKey decryptSecretKey(String cipheredKey, PrivateKey key) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        byte[] encodedKey = decrypt(cipheredKey, key);
        return AESKeyGenerator.fromEncoded(encodedKey);
    }


    /* ========================================================== */
    /* ====[                  Sign/Verify                   ]==== */
    /* ========================================================== */

    public static String sign(byte[] data, PrivateKey key) throws Exception {
        Signature privateSignature = Signature.getInstance("SHA256withRSA");
        privateSignature.initSign(key);
        privateSignature.update(data);
        byte[] signatureBytes = privateSignature.sign();
        return Base64.getEncoder().encodeToString(signatureBytes);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean verify(byte[] data, String signature, PublicKey key) throws Exception {
        Signature publicSignature = Signature.getInstance("SHA256withRSA");
        publicSignature.initVerify(key);
        publicSignature.update(data);
        byte[] signatureBytes = Base64.getDecoder().decode(signature);
        return publicSignature.verify(signatureBytes);
    }

    public static String sign(String dataString, PrivateKey key) throws Exception {
        byte[] data = Base64.getDecoder().decode(dataString);
        Signature privateSignature = Signature.getInstance("SHA256withRSA");
        privateSignature.initSign(key);
        privateSignature.update(data);
        byte[] signatureBytes = privateSignature.sign();
        return Base64.getEncoder().encodeToString(signatureBytes);
    }

    public static boolean verify(String dataString, String signature, PublicKey key) throws Exception {
        byte[] data = Base64.getDecoder().decode(dataString);
        Signature publicSignature = Signature.getInstance("SHA256withRSA");
        publicSignature.initVerify(key);
        publicSignature.update(data);
        byte[] signatureBytes = Base64.getDecoder().decode(signature);
        return publicSignature.verify(signatureBytes);
    }

}
