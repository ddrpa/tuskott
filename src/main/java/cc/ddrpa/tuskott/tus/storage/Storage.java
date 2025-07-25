package cc.ddrpa.tuskott.tus.storage;

import cc.ddrpa.tuskott.exception.BlobAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 暂存获取到的数据
 */
public interface Storage {

    /**
     * 创建一个文件块
     *
     * @param resourceId
     * @throws BlobAccessException
     * @throws IOException
     */
    void create(String resourceId) throws BlobAccessException, IOException;

    /**
     * 从指定偏移量开始写入数据到指定的文件块
     *
     * @param resourceId   文件块 ID
     * @param inputStream  输入流
     * @param uploadOffset 写入偏移量
     * @return
     * @throws BlobAccessException
     * @throws IOException
     */
    Long write(String resourceId, InputStream inputStream, Long uploadOffset)
            throws BlobAccessException, IOException;

    /**
     * 批量移除文件块
     */
    void remove(List<String> resourceIds);

    /**
     * 移除文件块
     */
    void remove(String resourceId);

    /**
     * 返回 InputStream 以便流式读取数据
     *
     * @param resourceId
     * @return
     * @throws IOException
     * @throws BlobAccessException
     */
    InputStream streaming(String resourceId) throws IOException, BlobAccessException;

    /**
     * 将上传进度回退到指定位置
     *
     * @param resourceId
     * @param offset
     */
    default void rollback(String resourceId, Long offset) {
    }
}