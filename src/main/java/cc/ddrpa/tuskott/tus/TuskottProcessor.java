package cc.ddrpa.tuskott.tus;

import cc.ddrpa.tuskott.autoconfigure.TuskottProperties;
import cc.ddrpa.tuskott.event.EventCallback;
import cc.ddrpa.tuskott.event.UploadSuccessEvent;
import cc.ddrpa.tuskott.tus.exception.BlobAccessException;
import cc.ddrpa.tuskott.tus.exception.ChecksumMismatchException;
import cc.ddrpa.tuskott.tus.provider.StoreProvider;
import cc.ddrpa.tuskott.tus.provider.FileInfo;
import cc.ddrpa.tuskott.tus.provider.FileInfoProvider;
import cc.ddrpa.tuskott.tus.provider.LockManager;
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
    private final StoreProvider storeProvider;
    private final LockManager lockManager;
    private final DateTimeFormatter rfc7231DateTimeFormatter = DateTimeFormatter
        .ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
        .withZone(ZoneId.of("GMT"));

    private final List<EventCallback> onSuccessCallback = new ArrayList<>();
    private final List<EventCallback> onCreateCallback = new ArrayList<>();

    public TuskottProcessor(TuskottProperties tuskottProperties, FileInfoProvider fileInfoProvider,
        StoreProvider storeProvider, LockManager lockManager) {
        this.tuskottProperties = tuskottProperties;
        this.fileInfoProvider = fileInfoProvider;
        this.storeProvider = storeProvider;
        this.lockManager = lockManager;
    }

    public FileInfoProvider getFileInfoProvider() {
        return fileInfoProvider;
    }

    public StoreProvider getStoreProvider() {
        return storeProvider;
    }

    /**
     * An OPTIONS request MAY be used to gather information about the Server’s current
     * configuration
     *
     * @param request
     * @param response
     */
    public void options(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(ConstantsPool.HEADER_TUS_RESUMABLE, ConstantsPool.TUS_VERSION);
        response.setHeader(ConstantsPool.HEADER_TUS_VERSION, ConstantsPool.TUS_VERSION);
        response.setHeader(ConstantsPool.HEADER_TUS_EXTENSION, ConstantsPool.TUS_EXTENSION);
        response.setHeader(ConstantsPool.HEADER_TUS_MAX_SIZE,
            String.valueOf(tuskottProperties.getMaxStoreSize()));
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
    public void creation(HttpServletRequest request, HttpServletResponse response) {
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
        } else if (uploadLength.get() > tuskottProperties.getMaxStoreSize()) {
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
            fileInfo = creation(uploadLength.orElse(null),
                request.getHeader(ConstantsPool.HEADER_UPLOAD_METADATA));
        } catch (BlobAccessException | IOException e) {
            logger.error(e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        response.setStatus(HttpServletResponse.SC_CREATED);
        response.setHeader(ConstantsPool.HEADER_LOCATION,
            request.getRequestURL() + "/" + fileInfo.id());
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
        response.setHeader(ConstantsPool.HEADER_CACHE_CONTROL, "no-store");
        response.setHeader(ConstantsPool.HEADER_TUS_RESUMABLE, ConstantsPool.TUS_VERSION);
        response.setHeader(ConstantsPool.HEADER_TUS_MAX_SIZE,
            String.valueOf(tuskottProperties.getMaxStoreSize()));
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
        response.setHeader(ConstantsPool.HEADER_CACHE_CONTROL, "no-store");
        response.setHeader(ConstantsPool.HEADER_TUS_RESUMABLE, ConstantsPool.TUS_VERSION);
        response.setHeader(ConstantsPool.HEADER_TUS_MAX_SIZE,
            String.valueOf(tuskottProperties.getMaxStoreSize()));
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
            } else if (uploadLength.get() > tuskottProperties.getMaxStoreSize()) {
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

        if (!lockManager.acquireLock(fileInfoId)) {
            response.setStatus(ConstantsPool.HTTP_LOCKED);
            return;
        }

        try (InputStream originInputStream = request.getInputStream()) {
            BoundedInputStream boundedInputStream = BoundedInputStream.builder()
                .setInputStream(originInputStream)
                .setMaxCount(tuskottProperties.getMaxRequestSize())
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
                // TODO 如果上传完成，将文件进行转存
                // TODO 清理缓存
                uploadSuccessCallback(new UploadSuccessEvent(fileInfo));
                fileInfoProvider.complete(fileInfoId, true);
            }
            Long contentLength = boundedInputStream.getCount();

        } catch (FileNotFoundException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } catch (BlobAccessException | IOException e) {
            logger.error(e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            lockManager.releaseLock(fileInfoId);
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
        response.setHeader(ConstantsPool.HEADER_CACHE_CONTROL, "no-store");
        response.setHeader(ConstantsPool.HEADER_TUS_RESUMABLE, ConstantsPool.TUS_VERSION);
        response.setHeader(ConstantsPool.HEADER_TUS_MAX_SIZE,
            String.valueOf(tuskottProperties.getMaxStoreSize()));
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
//        tusEventHandler.onTermination(new UploadTerminatedEvent());
    }

    protected boolean checkTusResumable(HttpServletRequest request) {
        return ConstantsPool.TUS_VERSION.equalsIgnoreCase(
            request.getHeader(ConstantsPool.HEADER_TUS_RESUMABLE));
    }

    protected boolean checkContentType(HttpServletRequest request) {
        return ConstantsPool.UPLOAD_CONTENT_TYPE.equalsIgnoreCase(request.getContentType());
    }

    protected Optional<Long> extractUploadOffset(HttpServletRequest request) {
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
    protected Optional<Long> extractUploadLength(HttpServletRequest request) {
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

    protected boolean checkUploadDeferLength(HttpServletRequest request) {
        String uploadDeferLength = request.getHeader(ConstantsPool.HEADER_UPLOAD_DEFER_LENGTH);
        return "1".equalsIgnoreCase(uploadDeferLength);
    }

    /**
     * 创建文件上传计划
     *
     * @return
     */
    private FileInfo creation(@Nullable Long uploadLength, @Nullable String metadata)
        throws BlobAccessException, IOException {
        String fileInfoID = UUID.randomUUID().toString().replaceAll("-", "");
        FileInfo fileInfo = fileInfoProvider.create(fileInfoID, uploadLength, metadata);
        storeProvider.create(fileInfoID);
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
        throws FileNotFoundException, BlobAccessException {
        Long newUploadOffset = storeProvider.write(fileInfoId, ins, uploadOffset);
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
     * @throws FileNotFoundException
     * @throws BlobAccessException
     * @throws ChecksumMismatchException
     */
    private Long patchWithChecksum(String fileInfoId, InputStream ins, Long uploadOffset,
        byte[] expectedChecksum, MessageDigest messageDigest)
        throws FileNotFoundException, BlobAccessException, ChecksumMismatchException {
        DigestInputStream digestInputStream = new DigestInputStream(ins, messageDigest);
        Long newUploadOffset = storeProvider.write(fileInfoId, digestInputStream, uploadOffset);
        if (!MessageDigest.isEqual(expectedChecksum, messageDigest.digest())) {
            storeProvider.rollback(fileInfoId, uploadOffset);
            throw new ChecksumMismatchException("checksum mismatch");
        }
        fileInfoProvider.patch(fileInfoId, newUploadOffset);
        return newUploadOffset;
    }

    /**
     * 删除过期文件
     */
    private void cleanExpiredFileInfo() {
        List<String> expiredFileInfoIds = fileInfoProvider.expire(true);
        storeProvider.remove(expiredFileInfoIds);
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
            storeProvider.remove(List.of(fileInfoId));
        }
    }

    /**
     * 注册回调函数
     *
     * @param onSuccessCallback
     * @param onCreateCallback
     */
    public void registerCallBack(List<EventCallback> onSuccessCallback,
        List<EventCallback> onCreateCallback) {
        if (!onSuccessCallback.isEmpty()) {
            this.onSuccessCallback.addAll(onSuccessCallback);
        }
        if (!onCreateCallback.isEmpty()) {
            this.onCreateCallback.addAll(onCreateCallback);
        }
    }

    private void uploadSuccessCallback(UploadSuccessEvent event) {
        for (EventCallback callback : onSuccessCallback) {
            CompletableFuture.runAsync(() -> {
                try {
                    callback.method().invoke(callback.bean(), event);
                } catch (Exception e) {
                    logger.trace("upload success callback error", e);
                }
            });
        }
        logger.trace("upload success callback");
    }
}