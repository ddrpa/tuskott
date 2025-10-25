package cc.ddrpa.tuskott;

public class ConstantsPool {

    public static final String HEADER_TUS_VERSION = "Tus-Version";
    public static final String TUS_VERSION = "1.0.0";
    // TODO concatenation
    // DO NOT SUPPORT checksum-trailer
    public static final String HEADER_ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";
    public static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Location, Upload-Offset, Upload-Length, Tus-Version, Tus-Resumable, Tus-Extension, Tus-Max-Size, Upload-Checksum";
    public static final String CACHE_CONTROL_NO_STORE = "no-store";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String UPLOAD_CONTENT_TYPE = "application/offset+octet-stream";
    public static final String PROBLEM_JSON_CONTENT_TYPE = "application/problem+json; charset=UTF-8";

    public static final String HEADER_UPLOAD_OFFSET = "Upload-Offset";
    public static final String HEADER_UPLOAD_LENGTH = "Upload-Length";
    public static final String HEADER_CONTENT_LENGTH = "Content-Length";
    public static final String HEADER_LOCATION = "Location";
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

    public static final String PROBLEM_DETAIL_MALFORMED_REQUEST_GENERIC = """
            {
                "type": "https://tus.io/protocols/resumable-upload#core-protocol",
                "title": "Malformed Tus request",
                "status": 400,
                "detail": "The request is missing required Tus headers or contains invalid metadata.",
                "instance": "%s"
            }""";

    public static final String PROBLEM_DETAIL_MISSING_UPLOAD_LENGTH = """
            {
                "type": "https://tus.io/protocols/resumable-upload#upload-defer-length",
                "title": "Missing Upload-Length request header",
                "status": 400,
                "detail": "The Upload-Defer-Length request and response header indicates that the size of the upload is not known currently and will be transferred later. Its value MUST be 1. If the length of an upload is not deferred, this header MUST be omitted.",
                "instance": "%s"
            }""";

    public static final String PROBLEM_DETAIL_MISSING_UPLOAD_OFFSET = """
            {
                "type": "https://tus.io/protocols/resumable-upload#upload-offset",
                "title": "Missing upload-Offset request header",
                "status": 400,
                "detail": "The Upload-Offset request and response header indicates a byte offset within a resource. The value MUST be a non-negative integer.",
                "instance": "%s"
            }""";

    public static final String PROBLEM_DETAIL_UNREADABLE_CHECKSUM_REQUEST_HEADER = """
            {
                "type": "https://tus.io/protocols/resumable-upload#checksum",
                "title": "Unreadable checksum request header",
                "status": 400,
                "detail": "The Upload-Checksum request header contains information about the checksum of the current body payload. The header MUST consist of the name of the used checksum algorithm and the Base64 encoded checksum separated by a space.",
                "instance": "%s"
            }""";

    public static final String PROBLEM_DETAIL_UNSUPPORTED_CHECKSUM_ALGORITHM = """
            {
                "type": "https://tus.io/protocols/resumable-upload#checksum",
                "title": "Checksum algorithm not supported",
                "status": 400,
                "detail": "Checksum algorithm is not supported by the server.",
                "instance": "%s"
            }""";

    public static final String PROBLEM_DETAIL_UPLOAD_RESOURCE_NOT_FOUND = """
            {
                "type": "https://tus.io/protocols/resumable-upload#core-protocol",
                "title": "Upload not found",
                "status": 404,
                "detail": "The requested upload resource does not exist or has been terminated.",
                "instance": "%s"
            }""";

    public static final String PROBLEM_DETAIL_OFFSET_MISMATCH = """
            {
                "type": "https://tus.io/protocols/resumable-upload#core-protocol",
                "title": "Upload offset mismatch",
                "status": 409,
                "detail": "Client sent Upload-Offset %d but server expected %d.",
                "instance": "%s",
                "extensions": {
                    "expected_offset": %d,
                    "received_offset": %d
                }
            }""";

    public static final String PROBLEM_DETAIL_RESOURCE_GONE = """
            {
                "type": "https://tus.io/protocols/resumable-upload#core-protocol",
                "title": "Upload resource no longer available",
                "status": 410,
                "detail": "The upload was previously terminated and can no longer be accessed.",
                "instance": "%s"
            }""";


    public static final String PROBLEM_DETAIL_UNSUPPORTED_TUS_VERSION = """
            {
                "type": "https://tus.io/protocols/resumable-upload#tus-resumable",
                "title": "Unsupported Tus protocol version",
                "status": 412,
                "detail": "The client requested Tus protocol version is not supported by this server.",
                "instance": "%s",
                "extensions": {
                    "supported_versions": ["1.0.0"]
                }
            }""";

    public static final String PROBLEM_DETAIL_REQUEST_ENTITY_TOO_LARGE = """
            {
                "type": "https://tus.io/protocols/resumable-upload#core-protocol",
                "title": "File exceeds maximum allowed size",
                "status": 413,
                "detail": "The total upload size exceeds the server limit of %d bytes.",
                "instance": "%s"
            }""";

    public static final String PROBLEM_DETAIL_UNSUPPORTED_MEDIA_TYPE = """
            {
                "type": "https://tus.io/protocols/resumable-upload#core-protocol",
                "title": "Unsupported Content-Type",
                "status": 415,
                "detail": "The request Content-Type is not supported. Expected 'application/offset+octet-stream'.",
                "instance": "%s"
            }
            """;

    public static final String PROBLEM_DETAIL_RESOURCE_LOCKED = """
            {
                "type": "https://tus.io/protocols/resumable-upload#core-protocol",
                "title": "Upload resource is locked",
                "status": 423,
                "detail": "The upload resource is currently locked due to another active request or incomplete operation.",
                "instance": "%s"
            }""";

    public static final String PROBLEM_DETAIL_CHECKSUM_MISMATCH = """
            {
                "type": "https://tus.io/protocols/resumable-upload#checksum",
                "title": "Checksum verification failed",
                "status": 460,
                "detail": "The uploaded data does not match the provided Upload-Checksum value.",
                "instance": "%s"
            }
            """;

    public static final String PROBLEM_DETAIL_INTERNAL_SERVER_ERROR = """
            {
                "type": "about:blank",
                "title": "Internal server error",
                "status": 500,
                "detail": "An unexpected error occurred while processing the upload request: %s",
                "instance": "%s"
            }""";

    public static final String PROBLEM_DETAIL_SERVICE_UNAVAILABLE = """
            {
                "type": "about:blank",
                "title": "Storage backend unavailable",
                "status": 503,
                "detail": "The storage service is temporarily unavailable because of: %s.",
                "instance": "%s"
            }""";

    private ConstantsPool() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}