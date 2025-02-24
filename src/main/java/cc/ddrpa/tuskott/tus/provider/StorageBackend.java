package cc.ddrpa.tuskott.tus.provider;

import cc.ddrpa.tuskott.tus.exception.BlobAccessException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 操纵实际获取到的数据
 * TODO 写文件块、组合文件块、删除文件、删除文件块
 */
public interface StorageBackend {

    /**
     * 创建一个接收者
     *
     * @param fileInfoID
     * @throws BlobAccessException
     * @throws IOException
     */
    void create(String fileInfoID) throws BlobAccessException, IOException;

    /**
     * 按序写入
     *
     * @param fileInfoId
     * @param ins
     * @param uploadOffset
     * @return
     * @throws BlobAccessException
     * @throws IOException
     */
    Long write(String fileInfoId, InputStream ins, Long uploadOffset)
        throws BlobAccessException, IOException;

    /**
     * 移除数据
     *
     * @param expiredFileInfoIds
     */
    void remove(List<String> expiredFileInfoIds);

    /**
     * 创建可读流返回数据
     *
     * @param fileInfoId
     * @return
     * @throws IOException
     * @throws BlobAccessException
     */
    InputStream read(String fileInfoId) throws IOException, BlobAccessException;

    /**
     * 将上传进度回退到指定位置
     *
     * @param fileInfoId
     * @param offset
     */
    default void rollback(String fileInfoId, Long offset) {
    }
}