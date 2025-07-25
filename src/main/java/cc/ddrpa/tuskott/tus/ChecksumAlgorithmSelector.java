package cc.ddrpa.tuskott.tus;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ChecksumAlgorithmSelector {

    public static final String SUPPORTED_CHECKSUM_ALGORITHM = "sha1,sha256,md5";

    private ChecksumAlgorithmSelector() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static MessageDigest getMessageDigest(String declaredAlgorithm)
            throws NoSuchAlgorithmException {
        return switch (declaredAlgorithm) {
            case "sha1" -> MessageDigest.getInstance("SHA-1");
            case "sha256" -> MessageDigest.getInstance("SHA-256");
            case "md5" -> MessageDigest.getInstance("MD5");
            default -> throw new NoSuchAlgorithmException(
                    "Unsupported checksum algorithm: " + declaredAlgorithm);
        };
    }
}