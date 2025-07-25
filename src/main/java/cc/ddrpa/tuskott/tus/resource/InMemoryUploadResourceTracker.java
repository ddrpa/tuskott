package cc.ddrpa.tuskott.tus.resource;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * In-memory implementation of {@link UploadResourceTracker}
 */
public class InMemoryUploadResourceTracker implements UploadResourceTracker {

    private final ConcurrentHashMap<String, UploadResource> store;

    public InMemoryUploadResourceTracker(Map<String, Object> properties) {
        this.store = new ConcurrentHashMap<>();
    }

    @Override
    public UploadResource create(String resourceId, Long uploadLength, String metadata) {
        UploadResource uploadResource;
        if (Objects.isNull(uploadLength)) {
            uploadResource = new UploadResource(resourceId, metadata);
        } else if (uploadLength > 0) {
            uploadResource = new UploadResource(resourceId, uploadLength, metadata);
        } else {
            // upload-length 为 0 时，表示上传一个空文件
            uploadResource = new UploadResource(resourceId, 0L, metadata);
        }
        store.put(resourceId, uploadResource);
        return uploadResource;
    }

    @Override
    public UploadResource patch(String resourceId, long newUploadOffset) {
        return store.get(resourceId).patch(newUploadOffset);
    }

    @Override
    public UploadResource head(String resourceId) {
        return store.get(resourceId);
    }

    @Override
    public UploadResource updateUploadLength(String resourceId, Long uploadLength) {
        return store.get(resourceId).uploadLength(uploadLength);
    }

    @Override
    public Stream<UploadResource> filter(Predicate<UploadResource> predicate) {
        return store.values()
                .stream()
                .filter(predicate);
    }

    @Override
    public void remove(List<String> resourceIds) {
        for (String resourceId : resourceIds) {
            store.remove(resourceId);
        }
    }

    @Override
    public void remove(String resourceId) {
        store.remove(resourceId);
    }
}