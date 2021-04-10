package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Arrays;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SecureLocationReport {

    private byte[] _cipheredKey;
    private byte[] _cipheredReport;

    public SecureLocationReport() {}

    public SecureLocationReport(byte[] _cipheredKey, byte[] _cipheredReport) {
        this._cipheredKey = _cipheredKey;
        this._cipheredReport = _cipheredReport;
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


    @Override
    public String toString() {
        return "SecureLocationReport{" +
                "_cipheredKey=" + Arrays.toString(_cipheredKey) +
                ", _cipheredLocationReport=" + Arrays.toString(_cipheredReport) +
                '}';
    }
}

