package cc.ddrpa.tuskott.tus;

import cc.ddrpa.tuskott.ConstantsPool;
import cc.ddrpa.tuskott.event.*;
import cc.ddrpa.tuskott.exception.BlobAccessException;
import cc.ddrpa.tuskott.exception.ChecksumMismatchException;
import cc.ddrpa.tuskott.properties.TuskottProperties;
import cc.ddrpa.tuskott.tus.lock.LockProvider;
import cc.ddrpa.tuskott.tus.resource.UploadResource;
import cc.ddrpa.tuskott.tus.resource.UploadResourceTracker;
import cc.ddrpa.tuskott.tus.storage.Storage;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.apache.commons.io.input.BoundedInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * check <a href="https://tus.io/protocols/resumable-upload">TUS Protocol</a> for details
 */
public class TuskottProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TuskottProcessor.class);

    private final TuskottProperties tuskottProperties;
    @Getter
    private final UploadResourceTracker tracker;
    @Getter
    private final Storage storage;
    @Getter
    private final LockProvider lockProvider;
    private final DateTimeFormatter rfc7231DateTimeFormatter = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
            .withZone(ZoneId.of("GMT"));

    private final List<TuskottEventCallback> postCreateCallback = new ArrayList<>();
    private final List<TuskottEventCallback> postCompleteCallback = new ArrayList<>();
    private final List<TuskottEventCallback> postTerminateCallback = new ArrayList<>();
    private final String enabledTusExtension;

    private final BiFunction<HttpServletRequest, String, String> uploadLocationHelperFunction;

    public TuskottProcessor(TuskottProperties tuskottProperties, UploadResourceTracker tracker, Storage storage, LockProvider lockProvider) {
        this.tuskottProperties = tuskottProperties;
        this.tracker = tracker;
        this.storage = storage;
        this.lockProvider = lockProvider;
        if (tuskottProperties.getBehindProxy().isEnable()) {
            TuskottProperties.BehindProxy behindProxyConfiguration = tuskottProperties.getBehindProxy();
            String fetchFromGivenUriHeader = behindProxyConfiguration.getHeader();
            uploadLocationHelperFunction = (req, resourceId) ->
                    req.getHeader(fetchFromGivenUriHeader) + "/" + resourceId;
        } else {
            uploadLocationHelperFunction = (req, resourceId) -> req.getRequestURI() + "/" + resourceId;
        }
        TuskottProperties.Extension extensionConfiguration = tuskottProperties.getExtension();
        List<String> enabledExtensions = new ArrayList<>();
        if (extensionConfiguration.isEnableCreation()) {
            enabledExtensions.add("creation");
        }
        enabledExtensions.add("creation-defer-length");
        enabledExtensions.add("expiration");
        if (extensionConfiguration.isEnableTermination()) {
            enabledExtensions.add("termination");
        }
        enabledExtensions.add("checksum");
        // enabledExtensions.add("creation-with-upload");
        this.enabledTusExtension = String.join(", ", enabledExtensions);
    }

    /**
     * An OPTIONS request MAY be used to gather information about the Server’s current
     * configuration
     */
    public void options(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(ConstantsPool.HEADER_ACCESS_CONTROL_EXPOSE_HEADERS,
                ConstantsPool.ACCESS_CONTROL_EXPOSE_HEADERS);
        response.setHeader(ConstantsPool.HEADER_TUS_RESUMABLE, ConstantsPool.TUS_VERSION);
        response.setHeader(ConstantsPool.HEADER_TUS_VERSION, ConstantsPool.TUS_VERSION);
        response.setHeader(ConstantsPool.HEADER_TUS_EXTENSION, enabledTusExtension);
        response.setHeader(ConstantsPool.HEADER_TUS_MAX_SIZE,
                String.valueOf(tuskottProperties.getMaxUploadLength()));
        response.setHeader(ConstantsPool.HEADER_TUS_CHECKSUM_ALGORITHM,
                ChecksumAlgorithmSelector.SUPPORTED_CHECKSUM_ALGORITHM);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    /**
     * Protocol Extensions: An empty POST request is used to create a new upload resource.
     * <p>
     * The Upload-Length header indicates the size of the entire upload in bytes.
     */
    public void create(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(ConstantsPool.HEADER_ACCESS_CONTROL_EXPOSE_HEADERS,
                ConstantsPool.ACCESS_CONTROL_EXPOSE_HEADERS);
        response.setHeader(ConstantsPool.HEADER_CACHE_CONTROL, ConstantsPool.CACHE_CONTROL_NO_STORE);
        response.setHeader(ConstantsPool.HEADER_TUS_RESUMABLE, ConstantsPool.TUS_VERSION);
        if (!checkTusResumable(request)) {
            response.setHeader(ConstantsPool.HEADER_TUS_VERSION, ConstantsPool.TUS_VERSION);
            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            return;
        }
        Optional<Long> optionalUploadLength = getLongValueFromRequestHeader(request, ConstantsPool.HEADER_UPLOAD_LENGTH);
        if (optionalUploadLength.isEmpty()) {
            // 如果客户端没有提供 Upload-Length，允许客户端在之后声明上传长度
            if (!checkUploadDeferLength(request)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
        } else if (optionalUploadLength.get() > tuskottProperties.getMaxUploadLength()) {
            // 客户端声明的上传总体积过大，返回 HTTP 413
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        UploadResource uploadResource;
        try {
            uploadResource = createUploadResource(optionalUploadLength.orElse(null),
                    request.getHeader(ConstantsPool.HEADER_UPLOAD_METADATA));
        } catch (BlobAccessException | IOException e) {
            logger.error(e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        response.setStatus(HttpServletResponse.SC_CREATED);
        response.setHeader(ConstantsPool.HEADER_LOCATION,
                uploadLocationHelperFunction.apply(request, uploadResource.getId()));

        // if uploadLength = 0，空文件，立即完成上传
        if (optionalUploadLength.isPresent() && optionalUploadLength.get() <= 0L) {
            completeUploadResource(uploadResource.getId());
        }
        // 如果客户端在创建上传时提供了 Upload-Length 和 Content-Type，视为 creation-with-upload
        if (optionalUploadLength.isPresent()
                && ConstantsPool.UPLOAD_CONTENT_TYPE.equalsIgnoreCase(request.getHeader(ConstantsPool.HEADER_CONTENT_TYPE))) {
            // 调用 path 方法，其中 uploadOffset = 0
            doPath(uploadResource, request, response, 0L);
        }
    }

    /**
     * 由服务端创建上传资源，由于这是服务端主动发起的调用，不会触发 postCreate 事件
     */
    public UploadResource createUploadResourceOnServerSide(String metadata) throws BlobAccessException, IOException {
        return createUploadResource(null, metadata);
    }

    /**
     * A HEAD request is used to determine the offset at which the upload should be continued.
     */
    public void head(@PathVariable("resource") String resourceId,
                     HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(ConstantsPool.HEADER_ACCESS_CONTROL_EXPOSE_HEADERS,
                ConstantsPool.ACCESS_CONTROL_EXPOSE_HEADERS);
        response.setHeader(ConstantsPool.HEADER_CACHE_CONTROL, ConstantsPool.CACHE_CONTROL_NO_STORE);
        response.setHeader(ConstantsPool.HEADER_TUS_RESUMABLE, ConstantsPool.TUS_VERSION);
        response.setHeader(ConstantsPool.HEADER_TUS_MAX_SIZE,
                String.valueOf(tuskottProperties.getMaxUploadLength()));
        if (!checkTusResumable(request)) {
            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            response.setHeader(ConstantsPool.HEADER_TUS_VERSION, ConstantsPool.TUS_VERSION);
            return;
        }
        UploadResource uploadResource = tracker.head(resourceId);
        if (Objects.isNull(uploadResource)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setHeader(ConstantsPool.HEADER_UPLOAD_EXPIRES,
                rfc7231DateTimeFormatter.format(uploadResource.getExpireTime()));
        if (Objects.isNull(uploadResource.getUploadDeferLength())) {
            // 如果客户端没有指定上传长度，服务端必须在每次 HEAD 响应中提醒
            response.setHeader(ConstantsPool.HEADER_UPLOAD_DEFER_LENGTH, "1");
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        response.setHeader(ConstantsPool.HEADER_UPLOAD_OFFSET,
                String.valueOf(uploadResource.getUploadOffset()));
        if (Objects.nonNull(uploadResource.getUploadLength())) {
            response.setHeader(ConstantsPool.HEADER_UPLOAD_LENGTH,
                    String.valueOf(uploadResource.getUploadLength()));

        }
    }

    /**
     * the Client uses the PATCH method to resume the upload
     */
    public void patch(@PathVariable("resource") String resourceId,
                      HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(ConstantsPool.HEADER_ACCESS_CONTROL_EXPOSE_HEADERS,
                ConstantsPool.ACCESS_CONTROL_EXPOSE_HEADERS);
        response.setHeader(ConstantsPool.HEADER_CACHE_CONTROL, ConstantsPool.CACHE_CONTROL_NO_STORE);
        response.setHeader(ConstantsPool.HEADER_TUS_RESUMABLE, ConstantsPool.TUS_VERSION);
        response.setHeader(ConstantsPool.HEADER_TUS_MAX_SIZE,
                String.valueOf(tuskottProperties.getMaxUploadLength()));
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

        UploadResource uploadResource = tracker.head(resourceId);
        if (Objects.isNull(uploadResource)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setHeader(ConstantsPool.HEADER_UPLOAD_EXPIRES,
                rfc7231DateTimeFormatter.format(uploadResource.getExpireTime()));

        // 如果客户端之前请求在上传时指定上传长度，必须在第一个 PATCH 中提供 Upload-Length
        if (Boolean.TRUE.equals(uploadResource.getUploadDeferLength())) {
            Optional<Long> optionalUploadLength = getLongValueFromRequestHeader(request, ConstantsPool.HEADER_UPLOAD_LENGTH);
            if (optionalUploadLength.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            } else if (optionalUploadLength.get() > tuskottProperties.getMaxUploadLength()) {
                response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                return;
            }
            tracker.updateUploadLength(resourceId, optionalUploadLength.get());
        }

        // The Upload-Offset request header MUST be included and
        // its value MUST be equal or smaller to the current offset of the file.
        Optional<Long> optionalUploadOffset = getLongValueFromRequestHeader(request, ConstantsPool.HEADER_UPLOAD_OFFSET);
        if (optionalUploadOffset.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (optionalUploadOffset.get() > uploadResource.getUploadOffset()) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            return;
        }
        doPath(uploadResource, request, response, optionalUploadOffset.get());
    }

    private void doPath(UploadResource uploadResource, HttpServletRequest request, HttpServletResponse response, long uploadOffset) {
        String resourceId = uploadResource.getId();
        // 计算从客户端声明的 Upload-Offset 到完整上传的差值，与单次上传 chunk 上限比较取最小值作为上传上限
        // 防止上传溢出
        long maxChunkSizeAllowed = Math.min(tuskottProperties.getMaxChunkSize(),
                uploadResource.getUploadLength() - uploadOffset);

        // 如果客户端声明了 Upload-Checksum，需要计算 chunk 的 Checksum 并与提供值进行比较
        MessageDigest messageDigest = null;
        boolean checksumValidateForChunkRequired = false;
        byte[] expectedChecksum = {};
        String checksumRequest = request.getHeader(ConstantsPool.HEADER_UPLOAD_CHECKSUM);
        if (StringUtils.hasText(checksumRequest)) {
            String[] split = checksumRequest.split(" ");
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
            checksumValidateForChunkRequired = true;
        }

        if (!lockProvider.acquire(resourceId)) {
            response.setStatus(ConstantsPool.HTTP_LOCKED);
            return;
        }
        try (InputStream originInputStream = request.getInputStream()) {
            BoundedInputStream boundedInputStream = BoundedInputStream.builder()
                    .setInputStream(originInputStream)
                    .setMaxCount(maxChunkSizeAllowed)
                    .setPropagateClose(true)
                    .get();
            // 本 chunk 上传成功后的总上传量
            Long updatedUploadOffset;
            if (checksumValidateForChunkRequired) {
                updatedUploadOffset = patchWithChecksum(resourceId, boundedInputStream, uploadOffset, expectedChecksum, messageDigest);
            } else {
                updatedUploadOffset = patchWithoutChecksum(resourceId, boundedInputStream, uploadOffset);
            }
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            // MUST include the Upload-Offset header containing the new offset
            response.setHeader(ConstantsPool.HEADER_UPLOAD_OFFSET, String.valueOf(updatedUploadOffset));

            // 如果上传完成，触发回调并更新上传状态
            if (Objects.equals(updatedUploadOffset, uploadResource.getUploadLength())) {
                completeUploadResource(resourceId);
            }
        } catch (ChecksumMismatchException e) {
            response.setStatus(ConstantsPool.HTTP_CHECKSUM_MISMATCH);
        } catch (FileNotFoundException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } catch (BlobAccessException | IOException e) {
            logger.error(e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            lockProvider.release(resourceId);
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    /**
     * a way for the Client to terminate completed and unfinished uploads allowing the Server to
     * free up used resources.
     */
    public void termination(@PathVariable("resource") String resourceId,
                            HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(ConstantsPool.HEADER_ACCESS_CONTROL_EXPOSE_HEADERS,
                ConstantsPool.ACCESS_CONTROL_EXPOSE_HEADERS);
        response.setHeader(ConstantsPool.HEADER_CACHE_CONTROL, ConstantsPool.CACHE_CONTROL_NO_STORE);
        response.setHeader(ConstantsPool.HEADER_TUS_RESUMABLE, ConstantsPool.TUS_VERSION);
        response.setHeader(ConstantsPool.HEADER_TUS_MAX_SIZE,
                String.valueOf(tuskottProperties.getMaxUploadLength()));
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
        terminationUploadResource(resourceId);
    }

    private boolean checkTusResumable(HttpServletRequest request) {
        return ConstantsPool.TUS_VERSION.equalsIgnoreCase(
                request.getHeader(ConstantsPool.HEADER_TUS_RESUMABLE));
    }

    private boolean checkContentType(HttpServletRequest request) {
        return ConstantsPool.UPLOAD_CONTENT_TYPE.equalsIgnoreCase(request.getContentType());
    }

    private Optional<Long> getLongValueFromRequestHeader(HttpServletRequest request, String headerName) {
        String valueAsStr = request.getHeader(headerName);
        if (!StringUtils.hasText(valueAsStr)) {
            return Optional.empty();
        }
        Long actualValue = null;
        try {
            actualValue = Long.parseLong(valueAsStr);
            if (actualValue < 0) {
                actualValue = 0L;
            }
        } catch (Exception ignored) {
        }
        return Optional.ofNullable(actualValue);
    }

    private boolean checkUploadDeferLength(HttpServletRequest request) {
        String uploadDeferLength = request.getHeader(ConstantsPool.HEADER_UPLOAD_DEFER_LENGTH);
        return "1".equalsIgnoreCase(uploadDeferLength);
    }

    /**
     * 创建文件上传计划
     *
     * @param uploadLength 预计上传长度，null 表示 Upload-Defer-Length
     * @param metadata     上传元数据，包含了原始文件和上传者等信息
     * @return
     * @throws BlobAccessException
     * @throws IOException
     */
    private UploadResource createUploadResource(@Nullable Long uploadLength, @Nullable String metadata)
            throws BlobAccessException, IOException {
        // create resource id
        String resourceId = UUID.randomUUID().toString().replaceAll("-", "");
        // create and hold upload resource
        UploadResource uploadResource = tracker.create(resourceId, uploadLength, metadata);
        // create actual file in storage backend
        storage.create(resourceId);
        invokeCallback(new PostCreateEvent(uploadResource));
        return uploadResource;
    }

    /**
     * 完成上传资源，更新上传状态并触发回调事件
     *
     * @param resourceId
     */
    private void completeUploadResource(String resourceId) {
        UploadResource uploadResource = tracker.head(resourceId);
        invokeCallback(new PostCompleteEvent(uploadResource));
    }

    /**
     * 上传文件
     *
     * @param resourceId
     * @param ins
     * @param uploadOffset
     * @return
     * @throws FileNotFoundException
     * @throws BlobAccessException
     */
    private Long patchWithoutChecksum(String resourceId, InputStream ins, Long uploadOffset)
            throws BlobAccessException, IOException {
        Long newUploadOffset = storage.write(resourceId, ins, uploadOffset);
        tracker.patch(resourceId, newUploadOffset);
        return newUploadOffset;
    }

    /**
     * 上传文件，计算校验和，校验通过后更新上传进度
     *
     * @param resourceId
     * @param ins
     * @param uploadOffset
     * @param expectedChecksum
     * @param messageDigest
     * @return
     * @throws BlobAccessException
     * @throws ChecksumMismatchException
     * @throws IOException
     */
    private Long patchWithChecksum(String resourceId, InputStream ins, Long uploadOffset,
                                   byte[] expectedChecksum, MessageDigest messageDigest)
            throws BlobAccessException, ChecksumMismatchException, IOException {
        DigestInputStream digestInputStream = new DigestInputStream(ins, messageDigest);
        Long newUploadOffset = storage.write(resourceId, digestInputStream, uploadOffset);
        if (!MessageDigest.isEqual(expectedChecksum, messageDigest.digest())) {
            storage.rollback(resourceId, uploadOffset);
            throw new ChecksumMismatchException("checksum mismatch");
        }
        tracker.patch(resourceId, newUploadOffset);
        return newUploadOffset;
    }

    /**
     * 终止上传
     *
     * @param resourceId
     */
    private void terminationUploadResource(String resourceId) {
        UploadResource uploadResource = tracker.head(resourceId);
        if (Objects.nonNull(uploadResource)) {
            tracker.remove(resourceId);
            storage.remove(resourceId);
        }
        invokeCallback(new PostTerminateEvent(uploadResource));
    }

    /**
     * 注册回调函数
     *
     * @param postCreate
     * @param postFinish
     * @param postTerminate
     */
    public void registerCallBack(List<TuskottEventCallback> postCreate, List<TuskottEventCallback> postFinish,
                                 List<TuskottEventCallback> postTerminate) {
        if (!postCreate.isEmpty()) {
            this.postCreateCallback.addAll(postCreate);
        }
        if (!postFinish.isEmpty()) {
            this.postCompleteCallback.addAll(postFinish);
        }
        if (!postTerminate.isEmpty()) {
            this.postTerminateCallback.addAll(postTerminate);
        }
    }

    /**
     * 触发回调
     *
     * @param event
     */
    private void invokeCallback(TuskottEvent event) {
        List<TuskottEventCallback> callbacks;
        if (event instanceof PostCompleteEvent) {
            callbacks = postCompleteCallback;
        } else if (event instanceof PostCreateEvent) {
            callbacks = postCreateCallback;
        } else if (event instanceof PostTerminateEvent) {
            callbacks = postTerminateCallback;
        } else {
            callbacks = Collections.emptyList();
        }
        for (TuskottEventCallback callback : callbacks) {
            CompletableFuture.runAsync(() -> {
                try {
                    callback.method().invoke(callback.bean(), event);
                } catch (Exception ignored) {
                }
            });
        }
    }
}