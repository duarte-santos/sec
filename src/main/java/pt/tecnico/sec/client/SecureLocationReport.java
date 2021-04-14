package pt.tecnico.sec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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

    @Override
    public String toString() {
        return "SecureLocationReport{" +
                "_cipheredKey=" + Arrays.toString(_cipheredKey) +
                ", _cipheredLocationReport=" + Arrays.toString(_cipheredReport) +
                '}';
    }
}

