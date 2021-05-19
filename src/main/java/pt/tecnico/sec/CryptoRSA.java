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

public class CryptoRSA {

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

}
