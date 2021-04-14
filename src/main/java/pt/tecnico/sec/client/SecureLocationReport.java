package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.sec.AESKeyGenerator;
import pt.tecnico.sec.RSAKeyGenerator;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SecureLocationReport {

    private byte[] _cipheredKey;
    private byte[] _cipheredReport;
    private byte[] _signature;

    public SecureLocationReport() {}

    public SecureLocationReport(byte[] cipheredKey, byte[] cipheredReport, byte[] signature) {
        _cipheredKey = cipheredKey;
        _cipheredReport = cipheredReport;
        _signature = signature;
    }

    public SecureLocationReport(LocationReport locationReport, PublicKey cipherKey, PrivateKey signKey) throws Exception {
        // make secret key
        SecretKey secretKey = AESKeyGenerator.makeAESKey();

        // encrypt report with secret key
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] reportBytes = objectMapper.writeValueAsBytes(locationReport);
        _cipheredReport = AESKeyGenerator.encrypt(reportBytes, secretKey);

        // encrypt secret key with given public cipher key
        _cipheredKey = RSAKeyGenerator.encryptSecretKey(secretKey, cipherKey);

        // sign report with given private sign key
        _signature = RSAKeyGenerator.sign(reportBytes, signKey);
    }

    public byte[] get_cipheredKey() {
        return _cipheredKey;
    }

    public void set_cipheredKey(byte[] _cipheredKey) {
        this._cipheredKey = _cipheredKey;
    }

    public byte[] get_cipheredReport() {
        return _cipheredReport;
    }

    public void set_cipheredReport(byte[] _cipheredReport) {
        this._cipheredReport = _cipheredReport;
    }

    public byte[] get_signature() {
        return _signature;
    }

    public void set_signature(byte[] _signature) {
        this._signature = _signature;
    }

    public LocationReport decipherAndVerify(PrivateKey decipherKey, PublicKey verifyKey) throws Exception {
        LocationReport report = decipher(decipherKey);
        report.verify(_signature, verifyKey);
        return report;
    }

    public LocationReport decipher(PrivateKey decipherKey) throws Exception {
        // Decipher secret key
        SecretKey secretKey = RSAKeyGenerator.decryptSecretKey(_cipheredKey, decipherKey);
        // Decipher report
        byte[] data = AESKeyGenerator.decrypt(_cipheredReport, secretKey);
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(data, LocationReport.class);
    }

    @Override
    public String toString() {
        return "SecureLocationReport{" +
                "_cipheredKey=" + Arrays.toString(_cipheredKey) +
                ", _cipheredLocationReport=" + Arrays.toString(_cipheredReport) +
                '}';
    }
}

