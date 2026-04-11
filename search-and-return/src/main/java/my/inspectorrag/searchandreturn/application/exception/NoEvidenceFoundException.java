package my.inspectorrag.searchandreturn.application.exception;

public class NoEvidenceFoundException extends IllegalArgumentException {

    public NoEvidenceFoundException(String message) {
        super(message);
    }
}
