package cc.ddrpa.tuskott.tus.provider;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public interface FileInfoProvider {

    /**
     * 创建上传
     *
     * @param fileInfoID fileInfoID
     * @param uploadLength 上传长度
     * @param metadata 元数据
     * @return
     */
    FileInfo create(String fileInfoID, @Nullable Long uploadLength, @Nullable String metadata);

    /**
     * 更新上传进度
     *
     * @param fileInfoID fileInfoID
     * @param newUploadOffset 新的上传进度
     * @return
     */
    FileInfo patch(String fileInfoID, long newUploadOffset);

    /**
     * 获取上传状态
     *
     * @param fileInfoID fileInfoID
     * @return
     */
    FileInfo head(String fileInfoID);

    /**
     * 更新上传数据长度
     *
     * @param fileInfoID
     * @param uploadLength
     * @return
     */
    FileInfo updateUploadLength(String fileInfoID, Long uploadLength);

    /**
     * 返回已过期上传 ID 列表
     *
     * @param instantRemove 是否删除
     * @return
     */
    List<String> listExpired(Boolean instantRemove);

    /**
     * 完成上传
     *
     * @param fileInfoID
     */
    default void complete(String fileInfoID) {
        // nothing needed
    }

    /**
     * 批量删除
     *
     * @param fileInfoIDs
     */
    void remove(List<String> fileInfoIDs);

    /**
     * 根据 checksum 查找
     *
     * @param checksum
     * @return
     */
    default Optional<FileInfo> findByChecksum(String checksum) {
        return Optional.empty();
    }
}