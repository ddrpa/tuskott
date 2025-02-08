package cc.ddrpa.tuskott.tus;

public class ConstantsPool {

    public static final String TUS_VERSION = "1.0.0";
    // TODO concatenation
    // DO NOT SUPPORT checksum-trailer
    public static final String TUS_EXTENSION = "creation,creation-defer-length,expiration,termination,checksum,creation-with-upload";
    public static final String UPLOAD_CONTENT_TYPE = "application/offset+octet-stream";
    public static final String HEADER_UPLOAD_OFFSET = "Upload-Offset";
    public static final String HEADER_UPLOAD_LENGTH = "Upload-Length";
    public static final String HEADER_CONTENT_LENGTH = "Content-Length";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_LOCATION = "Location";
    public static final String HEADER_TUS_VERSION = "Tus-Version";
    public static final String HEADER_TUS_RESUMABLE = "Tus-Resumable";
    public static final String HEADER_TUS_EXTENSION = "Tus-Extension";
    public static final String HEADER_TUS_MAX_SIZE = "Tus-Max-Size";
    public static final String HEADER_X_HTTP_METHOD_OVERRIDE = "X-HTTP-Method-Override";
    public static final String HEADER_UPLOAD_METADATA = "Upload-Metadata";
    public static final String HEADER_UPLOAD_DEFER_LENGTH = "Upload-Defer-Length";
    public static final String HEADER_UPLOAD_EXPIRES = "Upload-Expires";
    public static final String HEADER_UPLOAD_CHECKSUM = "Upload-Checksum";
    public static final String HEADER_TUS_CHECKSUM_ALGORITHM = "Tus-Checksum-Algorithm";
    public static final String HEADER_CACHE_CONTROL = "Cache-Control";

    public static final int HTTP_LOCKED = 423;
    public static final int HTTP_CHECKSUM_MISMATCH = 460;
}