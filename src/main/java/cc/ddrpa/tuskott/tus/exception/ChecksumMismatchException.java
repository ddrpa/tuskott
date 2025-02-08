package cc.ddrpa.tuskott.tus.exception;

public class ChecksumMismatchException extends Exception {

    public ChecksumMismatchException(String message) {
        super(message);
    }

    public ChecksumMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}