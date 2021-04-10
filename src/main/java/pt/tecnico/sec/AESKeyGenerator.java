package pt.tecnico.sec;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;

import static javax.xml.bind.DatatypeConverter.printHexBinary;

public class AESKeyGenerator {

    /* ========================================================== */
    /* ====[                   Manage Key                   ]==== */
    /* ========================================================== */

    public static SecretKey makeAESKey() throws GeneralSecurityException {
        // get an AES private key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        Key key = keyGen.generateKey();
        byte[] encoded = key.getEncoded();
        System.out.println( "New AES secret key: " + printHexBinary(encoded) );
        return new SecretKeySpec(encoded, 0, 16, "AES");
    }

    public static SecretKey fromEncoded(byte[] encoded) {
        return new SecretKeySpec(encoded, 0, 16, "AES");
    }



    /* ========================================================== */
    /* ====[                Encrypt/Decrypt                 ]==== */
    /* ========================================================== */

    public static byte[] encrypt(byte[] data, SecretKey key) throws BadPaddingException, IllegalBlockSizeException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        byte[] iv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        IvParameterSpec ivspec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, ivspec);
        return cipher.doFinal(data);
    }

    public static byte[] decrypt(byte[] data, SecretKey key) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        byte[] iv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        IvParameterSpec ivspec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        cipher.init(Cipher.DECRYPT_MODE, key, ivspec);
        return cipher.doFinal(data);
    }


}
