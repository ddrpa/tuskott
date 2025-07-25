package cc.ddrpa.tuskott.tus.resource;

import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * 在上传期间保存状态
 */
public interface UploadResourceTracker {

    /**
     * 创建上传
     *
     * @param resourceId   resourceId
     * @param uploadLength 上传长度, null 表示
     * @param metadata     元数据
     * @return
     */
    UploadResource create(String resourceId, @Nullable Long uploadLength, @Nullable String metadata);

    /**
     * 更新上传进度
     *
     * @param resourceId      resourceId
     * @param newUploadOffset 新的上传进度
     * @return
     */
    UploadResource patch(String resourceId, long newUploadOffset);

    /**
     * 获取上传状态
     *
     * @param resourceId resourceId
     * @return
     */
    UploadResource head(String resourceId);

    /**
     * 更新上传数据长度
     *
     * @param resourceId
     * @param uploadLength
     * @return
     */
    UploadResource updateUploadLength(String resourceId, Long uploadLength);

    /**
     * 返回一个过滤后的流
     */
    Stream<UploadResource> filter(Predicate<UploadResource> predicate);

    /**
     * 删除
     *
     * @param resourceId
     */
    void remove(String resourceId);

    /**
     * 批量删除
     *
     * @param resourceIds
     */
    void remove(List<String> resourceIds);

    /**
     * 根据 checksum 查找
     *
     * @param checksum
     * @return
     */
    default Optional<UploadResource> findByChecksum(String checksum) {
        return Optional.empty();
    }
}