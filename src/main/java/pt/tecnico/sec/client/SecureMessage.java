package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.RSAKeyGenerator;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecureMessage {

    private String _cipheredKey;
    private String _cipheredMessage;
    private String _signature;

    public SecureMessage() {}

    public SecureMessage(String cipheredKey, String cipheredMessage, String signature) {
        _cipheredKey = cipheredKey;
        _cipheredMessage = cipheredMessage;
        _signature = signature;
    }

    public SecureMessage(byte[] messageBytes, PublicKey cipherKey, PrivateKey signKey) throws Exception {
        // make secret key
        SecretKey secretKey = AESKeyGenerator.makeAESKey();

        // encrypt message with secret key
        _cipheredMessage = AESKeyGenerator.encrypt(messageBytes, secretKey);

        // encrypt secret key with given public cipher key
        _cipheredKey = RSAKeyGenerator.encryptSecretKey(secretKey, cipherKey);

        // sign message with given private sign key
        _signature = RSAKeyGenerator.sign(messageBytes, signKey);
    }

    public SecureMessage(byte[] messageBytes, SecretKey secretKey, PublicKey cipherKey, PrivateKey signKey) throws Exception {
        // encrypt message with secret key
        _cipheredMessage = AESKeyGenerator.encrypt(messageBytes, secretKey);

        // encrypt secret key with given public cipher key
        _cipheredKey = RSAKeyGenerator.encryptSecretKey(secretKey, cipherKey);

        // sign message with given private sign key
        _signature = RSAKeyGenerator.sign(messageBytes, signKey);
    }

    public byte[] decipherAndVerify(PrivateKey decipherKey, PublicKey verifyKey) throws Exception {
        byte[] messageBytes = decipher(decipherKey);
        verify(messageBytes, verifyKey);
        return messageBytes;
    }

    public byte[] decipher(PrivateKey decipherKey) throws Exception {
        // Decipher secret key
        SecretKey secretKey = getSecretKey(decipherKey);

        // Decipher message
        return AESKeyGenerator.decrypt(_cipheredMessage, secretKey);
    }

    public SecretKey getSecretKey(PrivateKey decipherKey) throws Exception {
        return RSAKeyGenerator.decryptSecretKey(_cipheredKey, decipherKey);
    }

    public void verify(byte[] messageBytes, PublicKey verifyKey) throws Exception {
        if (_signature == null || !RSAKeyGenerator.verify(messageBytes, _signature, verifyKey))
            throw new IllegalArgumentException("Signature verify failed!");
    }

    public String get_cipheredKey() {
        return _cipheredKey;
    }

    public void set_cipheredKey(String _cipheredKey) {
        this._cipheredKey = _cipheredKey;
    }

    public String get_cipheredMessage() {
        return _cipheredMessage;
    }

    public void set_cipheredMessage(String _cipheredMessage) {
        this._cipheredMessage = _cipheredMessage;
    }

    public String get_signature() {
        return _signature;
    }

    public void set_signature(String _signature) {
        this._signature = _signature;
    }

    @Override
    public String toString() {
        return "SecureMessage{" +
                "_cipheredKey='" + _cipheredKey + '\'' +
                ", _cipheredMessage='" + _cipheredMessage + '\'' +
                ", _signature='" + _signature + '\'' +
                '}';
    }
}
