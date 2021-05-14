package pt.tecnico.sec;

import org.apache.tomcat.util.http.fileupload.FileUtils;
import pt.tecnico.sec.server.exception.InvalidSignatureException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static pt.tecnico.sec.Constants.KEYS_PATH;

public class RSAKeyGenerator {

    /* ========================================================== */
    /* ====[                      Main                      ]==== */
    /* ========================================================== */

    public static void main(String[] args) throws Exception {

        try {
            int userCount = Integer.parseInt(args[0]);
            int serverCount = Integer.parseInt(args[1]);
            if (userCount <= 0 || serverCount <= 0)
                throw new NumberFormatException();

            writeKeyPairs(userCount, serverCount);
        }
        catch (NumberFormatException e) {
            System.out.println("Arguments must positive integers.");
            System.out.println("USAGE: ./mvnw spring-boot:run -Dspring-boot.run.arguments=\"[userCount] [serverCount]\" -Dstart-class=pt.tecnico.sec.RSAKeyGenerator");
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void writeKeyPairs(int userCount, int serverCount) throws Exception {
        try {
            // create key directory if it doesnt exist already
            File directory = new File(KEYS_PATH);
            if (!directory.exists()) {
                directory.mkdir();
            }

            // clean key directory before generating new keys
            FileUtils.cleanDirectory(new File(KEYS_PATH));
            for (int id = 0; id < userCount; id++) {
                writeKeyPair(KEYS_PATH + "c" + id);
            }
            for (int id = 0; id < serverCount; id++) {
                writeKeyPair(KEYS_PATH + "s" + id);
            }
            writeKeyPair(KEYS_PATH + "ha");
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
        }

        System.out.println("Done writing RSA Key Pairs.");
    }


    /* ========================================================== */
    /* ====[                 Manage KeyPair                 ]==== */
    /* ========================================================== */

    private static void writeKeyPair(String keyPath) throws GeneralSecurityException, IOException {
        // get an AES private key
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keys = keyGen.generateKeyPair();

        PrivateKey privKey = keys.getPrivate();
        byte[] privKeyEncoded = privKey.getEncoded();
        PublicKey pubKey = keys.getPublic();
        byte[] pubKeyEncoded = pubKey.getEncoded();

        FileOutputStream privFos = new FileOutputStream(keyPath + ".priv");
        privFos.write(privKeyEncoded);
        privFos.close();
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
        //System.out.println("Reading private key from file " + privateKeyPath + " ...");
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
    public static boolean verify(byte[] data, String signature, PublicKey key) {
        try {
            Signature publicSignature = Signature.getInstance("SHA256withRSA");
            publicSignature.initVerify(key);
            publicSignature.update(data);
            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            return publicSignature.verify(signatureBytes);
        } catch (Exception e) {
            throw new InvalidSignatureException("Invalid signature");
        }
    }


    /* ========================================================== */
    /* ====[              Auxiliary Functions               ]==== */
    /* ========================================================== */

    public static PublicKey readClientPublicKey(int clientId) throws GeneralSecurityException, IOException {
        return readPublicKey(KEYS_PATH + "c" + clientId + ".pub");
    }

    public static PublicKey readHAKey() throws GeneralSecurityException, IOException {
        return readPublicKey(KEYS_PATH + "ha.pub");
    }

    public static PublicKey readServerPublicKey(int serverId) throws GeneralSecurityException, IOException {
        return readPublicKey(KEYS_PATH + "s" + serverId + ".pub");
    }

    public static PublicKey[] readServersKeys(int serverCount) throws GeneralSecurityException, IOException {
        PublicKey[] serverKeys = new PublicKey[serverCount];
        for (int serverId = 0; serverId < serverCount; serverId++) {
            serverKeys[serverId] = RSAKeyGenerator.readServerPublicKey(serverId);
        }
        return serverKeys;
    }

}
