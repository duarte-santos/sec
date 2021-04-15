package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.RSAKeyGenerator;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SecureMessage {

    private byte[] _cipheredKey;
    private byte[] _cipheredMessage;
    private byte[] _signature;

    public SecureMessage() {}

    public SecureMessage(byte[] cipheredKey, byte[] cipheredMessage, byte[] signature) {
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

    public byte[] decipherAndVerify(PrivateKey decipherKey, PublicKey verifyKey) throws Exception {
        byte[] messageBytes = decipher(decipherKey);
        verify(messageBytes, verifyKey);
        return messageBytes;
    }

    public byte[] decipher(PrivateKey decipherKey) throws Exception {
        // Decipher secret key
        SecretKey secretKey = RSAKeyGenerator.decryptSecretKey(_cipheredKey, decipherKey);

        // Decipher message
        return AESKeyGenerator.decrypt(_cipheredMessage, secretKey);
    }

    public void verify(byte[] messageBytes, PublicKey verifyKey) throws Exception {
        if (_signature == null || !RSAKeyGenerator.verify(messageBytes, _signature, verifyKey))
            throw new IllegalArgumentException("Signature verify failed!"); //FIXME type of exception
    }

    public byte[] get_cipheredKey() {
        return _cipheredKey;
    }

    public void set_cipheredKey(byte[] _cipheredKey) {
        this._cipheredKey = _cipheredKey;
    }

    public byte[] get_cipheredMessage() {
        return _cipheredMessage;
    }

    public void set_cipheredMessage(byte[] _cipheredMessage) {
        this._cipheredMessage = _cipheredMessage;
    }

    public byte[] get_signature() {
        return _signature;
    }

    public void set_signature(byte[] _signature) {
        this._signature = _signature;
    }

    @Override
    public String toString() {
        return "SecureMessage{" +
                "_cipheredKey=" + Arrays.toString(_cipheredKey) +
                ", _cipheredMessage=" + Arrays.toString(_cipheredMessage) +
                ", _signature=" + Arrays.toString(_signature) +
                '}';
    }
}
