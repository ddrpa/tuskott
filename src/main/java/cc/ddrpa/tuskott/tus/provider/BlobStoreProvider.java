package cc.ddrpa.tuskott.tus.provider;

import cc.ddrpa.tuskott.tus.exception.BlobAccessException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * TODO 写文件块、组合文件块、删除文件、删除文件块
 */
public interface BlobStoreProvider {

    /**
     * 按序写入文件
     *
     * @param fileInfoId
     * @param ins
     * @param uploadOffset
     * @return
     * @throws IOException
     */
    Long write(String fileInfoId, InputStream ins, Long uploadOffset)
        throws FileNotFoundException, BlobAccessException;

    void create(String fileInfoID) throws BlobAccessException, IOException;

    void delete(List<String> expiredFileInfoIds);

    /**
     * 将上传进度回退到指定位置
     *
     * @param fileInfoId
     * @param uploadOffset
     */
    default void revoke(String fileInfoId, Long uploadOffset) {
    }
}