package cc.ddrpa.tuskott.tus.provider;

import jakarta.annotation.Nullable;
import java.util.List;

public interface FileInfoProvider {

    /**
     * 创建上传
     *
     * @param fileInfoID
     * @param uploadLength
     * @param metadata
     * @return
     */
    FileInfo create(String fileInfoID, @Nullable Long uploadLength, @Nullable String metadata);

    /**
     * 上传文件
     *
     * @param fileInfoID
     * @param newUploadOffset
     * @return
     */
    FileInfo patch(String fileInfoID, long newUploadOffset);

    /**
     * 获取文件上传状态
     *
     * @param fileInfoID
     * @return
     */
    FileInfo head(String fileInfoID);

    /**
     * 更新文件长度
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
    List<String> expire(Boolean instantRemove);

    /**
     * 完成上传
     *
     * @param fileInfoID
     * @param instantRemove 是否删除
     */
    void complete(String fileInfoID, Boolean instantRemove);

    /**
     * 批量删除上传
     *
     * @param fileInfoIDs
     */
    void remove(List<String> fileInfoIDs);
}