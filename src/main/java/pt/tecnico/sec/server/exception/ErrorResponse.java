package pt.tecnico.sec.server.exception;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@SuppressWarnings("unused")
@XmlRootElement(name = "error")
public class ErrorResponse {

    // General error message about nature of error
    private String _message;

    // Specific errors in API request processing
    private List<String> _details;

    // Constructor
    public ErrorResponse(String message, List<String> details) {
        super();
        _message = message;
        _details = details;
    }

    // Getter and setters

    public String getMessage() {
        return _message;
    }

    public void setMessage(String message) {
        _message = message;
    }

    public List<String> getDetails() {
        return _details;
    }

    public void setDetails(List<String> details) {
        _details = details;
    }
}