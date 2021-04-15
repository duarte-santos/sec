package pt.tecnico.sec.healthauthority;

import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.RSAKeyGenerator;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

public class SecureUsersList {
    private byte[] _cipheredKey;
    private byte[] _cipheredIds;
    private byte[] _signature;

    public SecureUsersList() {}

    public SecureUsersList(byte[] _cipheredKey, byte[] _cipheredIds, byte[] _signature) {
        this._cipheredKey = _cipheredKey;
        this._cipheredIds = _cipheredIds;
        this._signature = _signature;
    }

    public SecureUsersList(Integer[] userList, PublicKey cipherKey, PrivateKey signKey) throws Exception {
        // make secret key
        SecretKey secretKey = AESKeyGenerator.makeAESKey();

        // encrypt report with secret key
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] userListBytes = objectMapper.writeValueAsBytes(userList);
        _cipheredIds = AESKeyGenerator.encrypt(userListBytes, secretKey);

        // encrypt secret key with given public cipher key
        _cipheredKey = RSAKeyGenerator.encryptSecretKey(secretKey, cipherKey);

        // sign report with given private sign key
        _signature = RSAKeyGenerator.sign(userListBytes, signKey);
    }

    public Integer[] decipherAndVerify(PrivateKey decipherKey, PublicKey verifyKey) throws Exception {
        // Decipher secret key
        SecretKey secretKey = RSAKeyGenerator.decryptSecretKey(_cipheredKey, decipherKey);
        // Decipher report
        byte[] data = AESKeyGenerator.decrypt(_cipheredIds, secretKey);
        ObjectMapper objectMapper = new ObjectMapper();
        Integer[] userIds = objectMapper.readValue(data, Integer[].class);
        // Verify signature
        if (_signature == null || !RSAKeyGenerator.verify(data, _signature, verifyKey))
            throw new IllegalArgumentException("UserList signature failed!"); //FIXME type of exception
        return userIds;
    }

    public byte[] get_cipheredKey() {
        return _cipheredKey;
    }

    public void set_cipheredKey(byte[] _cipheredKey) {
        this._cipheredKey = _cipheredKey;
    }

    public byte[] get_cipheredIds() {
        return _cipheredIds;
    }

    public void set_cipheredIds(byte[] _cipheredIds) {
        this._cipheredIds = _cipheredIds;
    }

    public byte[] get_signature() {
        return _signature;
    }

    public void set_signature(byte[] _signature) {
        this._signature = _signature;
    }

    @Override
    public String toString() {
        return "SecureUsersList{" +
                "_cipheredKey=" + Arrays.toString(_cipheredKey) +
                ", _cipheredIds=" + Arrays.toString(_cipheredIds) +
                ", _signature=" + Arrays.toString(_signature) +
                '}';
    }
}
