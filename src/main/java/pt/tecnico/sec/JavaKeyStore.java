package pt.tecnico.sec;

import javax.crypto.SecretKey;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Enumeration;

import static pt.tecnico.sec.Constants.*;


/**
 * This class was adapted from:
 * @author https://www.baeldung.com/java-keystore
 */
public class JavaKeyStore {

    private KeyStore _keyStore;

    private String _type;
    private final String _name;
    private final String _password;

    public JavaKeyStore(String keyStoreType, String keyStorePassword, String keyStoreName) {
        _name = KEYSTORE_DIRECTORY + keyStoreName;
        _type = keyStoreType;
        _password = keyStorePassword;
    }

    public KeyStore getKeyStore() {
        return _keyStore;
    }

    public void createEmptyKeyStore() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        if (_type == null || _type.isEmpty()) {
            _type = KeyStore.getDefaultType();
        }
        _keyStore = KeyStore.getInstance(_type);

        // Load the KeyStore
        char[] pwdArray = _password.toCharArray();
        _keyStore.load(null, pwdArray);

        // Save the KeyStore
        try (FileOutputStream fos = new FileOutputStream(_name)) {
            _keyStore.store(fos, pwdArray);
        }
    }

    public void storeKeyStore() throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException {
        char[] pwdArray = _password.toCharArray();
        // Save the KeyStore
        try (FileOutputStream fos = new FileOutputStream(_name)) {
            _keyStore.store(fos, pwdArray);
        }
    }

    public void loadKeyStore() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        char[] pwdArray = _password.toCharArray();
        if (_keyStore == null) {
            _keyStore = KeyStore.getInstance(_type);
        }
        _keyStore.load(new FileInputStream(_name), pwdArray);
    }

    public void setEntry(String alias, KeyStore.SecretKeyEntry secretKeyEntry, KeyStore.ProtectionParameter protectionParameter) throws KeyStoreException {
        _keyStore.setEntry(alias, secretKeyEntry, protectionParameter);
    }

    KeyStore.Entry getEntry(String alias) throws UnrecoverableEntryException, NoSuchAlgorithmException, KeyStoreException {
        KeyStore.ProtectionParameter protectionParameter = new KeyStore.PasswordProtection(_password.toCharArray());
        return _keyStore.getEntry(alias, protectionParameter);
    }

    public void setKeyEntry(String alias, PrivateKey privateKey, String keyPassword, Certificate[] certificateChain) throws KeyStoreException {
        _keyStore.setKeyEntry(alias, privateKey, keyPassword.toCharArray(), certificateChain);
    }

    public void setCertificateEntry(String alias, Certificate certificate) throws KeyStoreException {
        _keyStore.setCertificateEntry(alias, certificate);
    }

    public Certificate getCertificate(String alias) throws KeyStoreException {
        return _keyStore.getCertificate(alias);
    }

    /* ===========[          GET KEYS          ]=========== */

    public SecretKey getSecretKey(String alias) throws KeyStoreException, UnrecoverableEntryException, NoSuchAlgorithmException {
        KeyStore.ProtectionParameter protectionParameter = new KeyStore.PasswordProtection(_password.toCharArray());
        KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) _keyStore.getEntry(alias, protectionParameter);
        return secretKeyEntry.getSecretKey();
    }

    public PrivateKey getPrivateKey(String alias) throws KeyStoreException, UnrecoverableEntryException, NoSuchAlgorithmException {
        KeyStore.ProtectionParameter protectionParameter = new KeyStore.PasswordProtection(_password.toCharArray());
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) _keyStore.getEntry(alias, protectionParameter);
        return privateKeyEntry.getPrivateKey();
    }

    public PublicKey getPublicKey(String alias) throws KeyStoreException {
        Certificate certificate = _keyStore.getCertificate(alias);
        return certificate.getPublicKey();
    }

    public KeyPair getKeyPair(String certificateAlias, String privateKeyAlias) throws KeyStoreException, UnrecoverableEntryException, NoSuchAlgorithmException {
        // Public Key
        Certificate certificate = _keyStore.getCertificate(certificateAlias);
        PublicKey publicKey = certificate.getPublicKey();
        // Private Key
        KeyStore.ProtectionParameter protectionParameter = new KeyStore.PasswordProtection(_password.toCharArray());
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) _keyStore.getEntry(privateKeyAlias, protectionParameter);
        PrivateKey privateKey = privateKeyEntry.getPrivateKey();
        // KeyPair
        return new KeyPair(publicKey, privateKey);
    }

    public PrivateKey getPersonalPrivateKey() throws KeyStoreException, UnrecoverableEntryException, NoSuchAlgorithmException {
        return getPrivateKey(KEYSTORE_PRIVATE_KEY);
    }

    public PublicKey getPersonalPublicKey() throws KeyStoreException {
        return getPublicKey(KEYSTORE_CERTIFICATE);
    }

    public KeyPair getPersonalKeyPair() throws KeyStoreException, UnrecoverableEntryException, NoSuchAlgorithmException {
        return getKeyPair(KEYSTORE_CERTIFICATE, KEYSTORE_PRIVATE_KEY);
    }

    public String toString() {
        return "Name: " + _name + ", Password: " + _password + ", Type: " + _type;
    }

    /* ===========[           DELETE           ]=========== */

    public void deleteEntry(String alias) throws KeyStoreException {
        _keyStore.deleteEntry(alias);
    }

    public void deleteKeyStore() throws KeyStoreException, IOException {
        Enumeration<String> aliases = _keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            _keyStore.deleteEntry(alias);
        }
        _keyStore = null;
        Files.delete(Paths.get(_name));
    }

}
