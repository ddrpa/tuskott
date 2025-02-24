package cc.ddrpa.tuskott.tus.exception;

public class BlobAccessException extends Exception {

    public BlobAccessException(String message) {
        super(message);
    }

    public BlobAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}