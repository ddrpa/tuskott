package cc.ddrpa.tuskott.tus;

import cc.ddrpa.tuskott.hook.EventCallback;
import cc.ddrpa.tuskott.hook.PostCreateEvent;
import cc.ddrpa.tuskott.hook.PostFinishEvent;
import cc.ddrpa.tuskott.hook.PostTerminateEvent;
import cc.ddrpa.tuskott.hook.TusEvent;
import cc.ddrpa.tuskott.properties.TuskottProperties;
import cc.ddrpa.tuskott.tus.exception.BlobAccessException;
import cc.ddrpa.tuskott.tus.exception.ChecksumMismatchException;
import cc.ddrpa.tuskott.tus.provider.FileInfo;
import cc.ddrpa.tuskott.tus.provider.FileInfoProvider;
import cc.ddrpa.tuskott.tus.provider.LockProvider;
import cc.ddrpa.tuskott.tus.provider.StorageBackend;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import org.apache.commons.io.input.BoundedInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * check <a href="https://tus.io/protocols/resumable-upload">TUS Protocol</a> for details
 */
public class TuskottProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TuskottProcessor.class);

    private final TuskottProperties tuskottProperties;
    private final FileInfoProvider fileInfoProvider;
    private final StorageBackend storageBackend;
    private final LockProvider lockProvider;
    private final DateTimeFormatter rfc7231DateTimeFormatter = DateTimeFormatter
        .ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
        .withZone(ZoneId.of("GMT"));

    private final List<EventCallback> postCreateCallback = new ArrayList<>();
    private final List<EventCallback> postFinishCallback = new ArrayList<>();
    private final List<EventCallback> postTerminateCallback = new ArrayList<>();

    private final BiFunction<HttpServletRequest, String, String> locationFunction;

    public TuskottProcessor(TuskottProperties tuskottProperties, FileInfoProvider fileInfoProvider,
        StorageBackend storageBackend, LockProvider lockProvider) {
        this.tuskottProperties = tuskottProperties;
        this.fileInfoProvider = fileInfoProvider;
        this.storageBackend = storageBackend;
        this.lockProvider = lockProvider;
        if (tuskottProperties.getBehindProxy()) {
            locationFunction = (req, fileInfoId) ->
                req.getHeader(tuskottProperties.getUriHeaderName()) + "/" + fileInfoId;
        } else {
            locationFunction = (req, fileInfoId) -> req.getRequestURI() + "/" + fileInfoId;
        }
    }

    public FileInfoProvider getFileInfoProvider() {
        return fileInfoProvider;
    }

    public StorageBackend getStoreBackend() {
        return storageBackend;
    }

    /**
     * An OPTIONS request MAY be used to gather information about the Server’s current
     * configuration
     *
     * @param request
     * @param response
     */
    public void options(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(ConstantsPool.HEADER_ACCESS_CONTROL_EXPOSE_HEADERS,
            ConstantsPool.ACCESS_CONTROL_EXPOSE_HEADERS);
        response.setHeader(ConstantsPool.HEADER_TUS_RESUMABLE, ConstantsPool.TUS_VERSION);
        response.setHeader(ConstantsPool.HEADER_TUS_VERSION, ConstantsPool.TUS_VERSION);
        response.setHeader(ConstantsPool.HEADER_TUS_EXTENSION, ConstantsPool.TUS_EXTENSION);
        response.setHeader(ConstantsPool.HEADER_TUS_MAX_SIZE,
            String.valueOf(tuskottProperties.getMaxBlobSize()));
        response.setHeader(ConstantsPool.HEADER_TUS_CHECKSUM_ALGORITHM,
            ChecksumAlgorithmSelector.SUPPORTED_CHECKSUM_ALGORITHM);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    /**
     * Protocol Extensions: An empty POST request is used to create a new upload resource.
     * <p>
     * The Upload-Length header indicates the size of the entire upload in bytes.
     *
     * @param request
     * @param response
     */
    public void create(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(ConstantsPool.HEADER_ACCESS_CONTROL_EXPOSE_HEADERS,
            ConstantsPool.ACCESS_CONTROL_EXPOSE_HEADERS);
        response.setHeader(ConstantsPool.HEADER_CACHE_CONTROL, "no-store");
        response.setHeader(ConstantsPool.HEADER_TUS_RESUMABLE, ConstantsPool.TUS_VERSION);
        if (!checkTusResumable(request)) {
            response.setHeader(ConstantsPool.HEADER_TUS_VERSION, ConstantsPool.TUS_VERSION);
            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            return;
        }
        Optional<Long> uploadLength = extractUploadLength(request);
        if (uploadLength.isEmpty()) {
            // 如果客户端没有提供 Upload-Length，允许客户端在之后声明上传长度
            if (!checkUploadDeferLength(request)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
        } else if (uploadLength.get() > tuskottProperties.getMaxBlobSize()) {
            // 客户端声明的上传体积过大
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        } else if (uploadLength.get() == 0L) {
            // NEED_CHECK 客户端要创建一个空文件，这里什么都不做
            response.setStatus(HttpServletResponse.SC_CREATED);
            return;
        }
        FileInfo fileInfo;
        try {
            fileInfo = create(uploadLength.orElse(null),
                request.getHeader(ConstantsPool.HEADER_UPLOAD_METADATA));
        } catch (BlobAccessException | IOException e) {
            logger.error(e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        response.setStatus(HttpServletResponse.SC_CREATED);
        response.setHeader(ConstantsPool.HEADER_LOCATION,
            locationFunction.apply(request, fileInfo.id()));
        // 判断是否为 creation-with-upload
        if (ConstantsPool.UPLOAD_CONTENT_TYPE.equalsIgnoreCase(
            request.getHeader(ConstantsPool.HEADER_CONTENT_TYPE)) && uploadLength.isPresent()) {
            try {
                // TODO support checksum,lock
                long newUploadOffset = patch(fileInfo.id(),
                    request.getInputStream(), 0L);
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                response.setHeader(ConstantsPool.HEADER_UPLOAD_OFFSET,
                    String.valueOf(newUploadOffset));
            } catch (BlobAccessException | IOException e) {
                logger.error(e.getMessage());
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    /**
     * A HEAD request is used to determine the offset at which the upload should be continued.
     *
     * @param request
     * @param response
     */
    public void head(@PathVariable("fileInfoID") String fileInfoId,
        HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(ConstantsPool.HEADER_ACCESS_CONTROL_EXPOSE_HEADERS,
            ConstantsPool.ACCESS_CONTROL_EXPOSE_HEADERS);
        response.setHeader(ConstantsPool.HEADER_CACHE_CONTROL, "no-store");
        response.setHeader(ConstantsPool.HEADER_TUS_RESUMABLE, ConstantsPool.TUS_VERSION);
        response.setHeader(ConstantsPool.HEADER_TUS_MAX_SIZE,
            String.valueOf(tuskottProperties.getMaxBlobSize()));
        if (!checkTusResumable(request)) {
            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            response.setHeader(ConstantsPool.HEADER_TUS_VERSION, ConstantsPool.TUS_VERSION);
            return;
        }
        FileInfo fileInfo = head(fileInfoId);
        if (Objects.isNull(fileInfo)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setHeader(ConstantsPool.HEADER_UPLOAD_EXPIRES,
            rfc7231DateTimeFormatter.format(fileInfo.expireTime()));
        if (Objects.isNull(fileInfo.uploadDeferLength())) {
            // 如果客户端没有指定上传长度，服务端必须在每次 HEAD 响应中提醒
            response.setHeader(ConstantsPool.HEADER_UPLOAD_DEFER_LENGTH, "1");
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        response.setHeader(ConstantsPool.HEADER_UPLOAD_OFFSET,
            String.valueOf(fileInfo.uploadOffset()));
        if (Objects.nonNull(fileInfo.uploadLength())) {
            response.setHeader(ConstantsPool.HEADER_UPLOAD_LENGTH,
                String.valueOf(fileInfo.uploadLength()));
        }
    }

    /**
     * the Client uses the PATCH method to resume the upload
     * <p>
     * TODO PATCH 请求没有计算 content-length + offset 是否会超出 max-size
     * TODO 未处理 Upload-Length 为 0 的情况
     *
     * @param request
     * @param response
     */
    public void patch(@PathVariable("fileInfoID") String fileInfoId,
        HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(ConstantsPool.HEADER_ACCESS_CONTROL_EXPOSE_HEADERS,
            ConstantsPool.ACCESS_CONTROL_EXPOSE_HEADERS);
        response.setHeader(ConstantsPool.HEADER_CACHE_CONTROL, "no-store");
        response.setHeader(ConstantsPool.HEADER_TUS_RESUMABLE, ConstantsPool.TUS_VERSION);
        response.setHeader(ConstantsPool.HEADER_TUS_MAX_SIZE,
            String.valueOf(tuskottProperties.getMaxBlobSize()));
        if (!checkTusResumable(request)) {
            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            response.setHeader(ConstantsPool.HEADER_TUS_VERSION, ConstantsPool.TUS_VERSION);
            return;
        }
        boolean contentTypeCheck = checkContentType(request);
        if (!contentTypeCheck) {
            response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }
        FileInfo fileInfo = head(fileInfoId);
        if (Objects.isNull(fileInfo)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setHeader(ConstantsPool.HEADER_UPLOAD_EXPIRES,
            rfc7231DateTimeFormatter.format(fileInfo.expireTime()));
        if (fileInfo.uploadDeferLength()) {
            // 如果客户端之前请求在上传时指定上传长度，必须在第一个 PATCH 中提供 Upload-Length
            Optional<Long> uploadLength = extractUploadLength(request);
            if (uploadLength.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            } else if (uploadLength.get() > tuskottProperties.getMaxBlobSize()) {
                response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                return;
            }
            fileInfoProvider.updateUploadLength(fileInfoId, uploadLength.get());
        }

        Optional<Long> uploadOffset = extractUploadOffset(request);
        if (uploadOffset.isEmpty()) {
            // The Upload-Offset request header MUST be included and
            // its value MUST be equal or smaller to the current offset of the file.
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (uploadOffset.get() > fileInfo.uploadOffset()) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            return;
        }
        // get Checksum
        MessageDigest messageDigest = null;
        boolean needChecksumValidate = false;
        byte[] expectedChecksum = {};
        String checksum = request.getHeader(ConstantsPool.HEADER_UPLOAD_CHECKSUM);
        if (StringUtils.hasText(checksum)) {
            String[] split = checksum.split(" ");
            if (split.length != 2) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            try {
                messageDigest = ChecksumAlgorithmSelector.getMessageDigest(split[0]);
            } catch (NoSuchAlgorithmException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            expectedChecksum = Base64.getDecoder().decode(split[1]);
            needChecksumValidate = true;
        }

        if (!lockProvider.acquire(fileInfoId)) {
            response.setStatus(ConstantsPool.HTTP_LOCKED);
            return;
        }

        try (InputStream originInputStream = request.getInputStream()) {
            BoundedInputStream boundedInputStream = BoundedInputStream.builder()
                .setInputStream(originInputStream)
                .setMaxCount(tuskottProperties.getMaxUpload())
                .setPropagateClose(true)
                .get();
            Long newUploadOffset;

            if (needChecksumValidate) {
                try {
                    newUploadOffset = patchWithChecksum(fileInfoId, boundedInputStream,
                        uploadOffset.get(), expectedChecksum, messageDigest);
                } catch (ChecksumMismatchException e) {
                    response.setStatus(ConstantsPool.HTTP_CHECKSUM_MISMATCH);
                    return;
                }
            } else {
                newUploadOffset = patch(fileInfoId, boundedInputStream,
                    uploadOffset.get());
            }
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            // It MUST include the Upload-Offset header containing the new offset
            response.setHeader(ConstantsPool.HEADER_UPLOAD_OFFSET, String.valueOf(newUploadOffset));
            if (Objects.equals(newUploadOffset, fileInfo.uploadLength())) {
                invokeCallback(new PostFinishEvent(fileInfo));
                fileInfoProvider.complete(fileInfoId);
            }
            Long contentLength = boundedInputStream.getCount();
        } catch (FileNotFoundException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } catch (BlobAccessException | IOException e) {
            logger.error(e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            lockProvider.release(fileInfoId);
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    /**
     * a way for the Client to terminate completed and unfinished uploads allowing the Server to
     * free up used resources.
     *
     * @param fileInfoId
     * @param request
     * @param response
     */
    public void termination(@PathVariable("fileInfoID") String fileInfoId,
        HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(ConstantsPool.HEADER_ACCESS_CONTROL_EXPOSE_HEADERS,
            ConstantsPool.ACCESS_CONTROL_EXPOSE_HEADERS);
        response.setHeader(ConstantsPool.HEADER_CACHE_CONTROL, "no-store");
        response.setHeader(ConstantsPool.HEADER_TUS_RESUMABLE, ConstantsPool.TUS_VERSION);
        response.setHeader(ConstantsPool.HEADER_TUS_MAX_SIZE,
            String.valueOf(tuskottProperties.getMaxBlobSize()));
        if (!checkTusResumable(request)) {
            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            response.setHeader(ConstantsPool.HEADER_TUS_VERSION, ConstantsPool.TUS_VERSION);
            return;
        }
        boolean contentTypeCheck = checkContentType(request);
        if (!contentTypeCheck) {
            response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }
        termination(fileInfoId);
    }

    private boolean checkTusResumable(HttpServletRequest request) {
        return ConstantsPool.TUS_VERSION.equalsIgnoreCase(
            request.getHeader(ConstantsPool.HEADER_TUS_RESUMABLE));
    }

    private boolean checkContentType(HttpServletRequest request) {
        return ConstantsPool.UPLOAD_CONTENT_TYPE.equalsIgnoreCase(request.getContentType());
    }

    private Optional<Long> extractUploadOffset(HttpServletRequest request) {
        String uploadOffsetAsStr = request.getHeader(ConstantsPool.HEADER_UPLOAD_OFFSET);
        if (!StringUtils.hasText(uploadOffsetAsStr)) {
            return Optional.empty();
        }
        Long uploadOffset = null;
        try {
            uploadOffset = Long.parseLong(uploadOffsetAsStr);
            if (uploadOffset < 0L) {
                return Optional.empty();
            }
        } catch (Exception ignored) {
        }
        return Optional.ofNullable(uploadOffset);
    }

    /**
     * indicates the size of the entire upload in bytes
     * <p>
     * value MUST be a non-negative integer
     *
     * @param request
     * @return
     */
    private Optional<Long> extractUploadLength(HttpServletRequest request) {
        String uploadLengthAsStr = request.getHeader(ConstantsPool.HEADER_UPLOAD_LENGTH);
        if (!StringUtils.hasText(uploadLengthAsStr)) {
            return Optional.empty();
        }
        Long uploadLength = null;
        try {
            uploadLength = Long.parseLong(uploadLengthAsStr);
            if (uploadLength < 0L) {
                return Optional.empty();
            }
        } catch (Exception ignored) {
        }
        return Optional.ofNullable(uploadLength);
    }

    private boolean checkUploadDeferLength(HttpServletRequest request) {
        String uploadDeferLength = request.getHeader(ConstantsPool.HEADER_UPLOAD_DEFER_LENGTH);
        return "1".equalsIgnoreCase(uploadDeferLength);
    }

    /**
     * 创建文件上传计划
     *
     * @return
     */
    private FileInfo create(@Nullable Long uploadLength, @Nullable String metadata)
        throws BlobAccessException, IOException {
        String fileInfoID = UUID.randomUUID().toString().replaceAll("-", "");
        FileInfo fileInfo = fileInfoProvider.create(fileInfoID, uploadLength, metadata);
        storageBackend.create(fileInfoID);
        invokeCallback(new PostCreateEvent(fileInfo));
        return fileInfo;
    }

    /**
     * 查询文件上传进度
     *
     * @param fileInfoID
     * @return
     */
    private FileInfo head(String fileInfoID) {
        return fileInfoProvider.head(fileInfoID);
    }

    /**
     * 上传文件
     *
     * @param fileInfoId
     * @param ins
     * @param uploadOffset
     * @return
     * @throws FileNotFoundException
     * @throws BlobAccessException
     */
    private Long patch(String fileInfoId, InputStream ins, Long uploadOffset)
        throws BlobAccessException, IOException {
        Long newUploadOffset = storageBackend.write(fileInfoId, ins, uploadOffset);
        fileInfoProvider.patch(fileInfoId, newUploadOffset);
        return newUploadOffset;
    }

    /**
     * 上传文件，计算校验和，校验通过后更新上传进度
     *
     * @param fileInfoId
     * @param ins
     * @param uploadOffset
     * @param expectedChecksum
     * @param messageDigest
     * @return
     * @throws BlobAccessException
     * @throws ChecksumMismatchException
     * @throws IOException
     */
    private Long patchWithChecksum(String fileInfoId, InputStream ins, Long uploadOffset,
        byte[] expectedChecksum, MessageDigest messageDigest)
        throws BlobAccessException, ChecksumMismatchException, IOException {
        DigestInputStream digestInputStream = new DigestInputStream(ins, messageDigest);
        Long newUploadOffset = storageBackend.write(fileInfoId, digestInputStream, uploadOffset);
        if (!MessageDigest.isEqual(expectedChecksum, messageDigest.digest())) {
            storageBackend.rollback(fileInfoId, uploadOffset);
            throw new ChecksumMismatchException("checksum mismatch");
        }
        fileInfoProvider.patch(fileInfoId, newUploadOffset);
        return newUploadOffset;
    }

    /**
     * 删除过期文件
     */
    private void cleanExpiredFileInfo() {
        List<String> expiredFileInfoIds = fileInfoProvider.listExpired(true);
        storageBackend.remove(expiredFileInfoIds);
    }

    /**
     * 终止上传
     *
     * @param fileInfoId
     */
    private void termination(String fileInfoId) {
        FileInfo fileInfo = fileInfoProvider.head(fileInfoId);
        if (Objects.nonNull(fileInfo)) {
            fileInfoProvider.remove(List.of(fileInfoId));
            storageBackend.remove(List.of(fileInfoId));
        }
        invokeCallback(new PostTerminateEvent(fileInfo));
    }

    /**
     * 注册回调函数
     *
     * @param postCreate
     * @param postFinish
     * @param postTerminate
     */
    public void registerCallBack(List<EventCallback> postCreate, List<EventCallback> postFinish,
        List<EventCallback> postTerminate) {
        if (!postCreate.isEmpty()) {
            this.postCreateCallback.addAll(postCreate);
        }
        if (!postFinish.isEmpty()) {
            this.postFinishCallback.addAll(postFinish);
        }
        if (!postTerminate.isEmpty()) {
            this.postTerminateCallback.addAll(postTerminate);
        }
    }

    /**
     * 触发上传成功回调
     *
     * @param event
     */
    private void invokeCallback(TusEvent event) {
        if (event instanceof PostFinishEvent) {
            for (EventCallback callback : postFinishCallback) {
                CompletableFuture.runAsync(() -> {
                    try {
                        callback.method().invoke(callback.bean(), event);
                    } catch (Exception ignored) {
                    }
                });
            }
        } else if (event instanceof PostCreateEvent) {
            for (EventCallback callback : postCreateCallback) {
                CompletableFuture.runAsync(() -> {
                    try {
                        callback.method().invoke(callback.bean(), event);
                    } catch (Exception ignored) {
                    }
                });
            }
        } else if (event instanceof PostTerminateEvent) {
            for (EventCallback callback : postTerminateCallback) {
                CompletableFuture.runAsync(() -> {
                    try {
                        callback.method().invoke(callback.bean(), event);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }
}