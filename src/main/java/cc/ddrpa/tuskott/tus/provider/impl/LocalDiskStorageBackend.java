package cc.ddrpa.tuskott.tus.provider.impl;

import cc.ddrpa.tuskott.tus.exception.BlobAccessException;
import cc.ddrpa.tuskott.tus.provider.StorageBackend;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class LocalDiskStorageBackend implements StorageBackend {

    /**
     * @param fileInfoID
     * @throws BlobAccessException
     * @throws IOException
     */
    @Override
    public void create(String fileInfoID) throws BlobAccessException, IOException {
        Path filePath = getFilePath(fileInfoID);
        File file = filePath.toFile();
        if (file.exists()) {
            throw new BlobAccessException("file already exists");
        }
        file.createNewFile();
    }

    @Override
    public Long write(String fileInfoId, InputStream ins, Long uploadOffset)
        throws FileNotFoundException, BlobAccessException {
        Path filePath = getFilePath(fileInfoId);
        File file = filePath.toFile();
        if (!file.exists()) {
            throw new FileNotFoundException(filePath.toString());
        }
        if (!file.canRead() || !file.canWrite() || !file.isFile()) {
            throw new BlobAccessException("file not readable or writable");
        }
        long transferred = 0L;
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            try {
                raf.seek(uploadOffset);
            } catch (IOException e) {
                throw new BlobAccessException(e.getMessage());
            }
            byte[] buffer = new byte[1024]; // 缓冲区大小
            while (true) {
                int bytesRead;
                try {
                    bytesRead = ins.read(buffer);
                    if (bytesRead < 0) {
                        break;
                    }
                    raf.write(buffer, 0, bytesRead);
                } catch (IOException e) {
                    // 传输被打断或文件写入失败，保存当前的状态
                    return uploadOffset + transferred;
                }
                transferred += bytesRead;
            }
        } catch (IOException e) {
            throw new BlobAccessException("file not readable or writable");
        }
        return uploadOffset + transferred;
    }

    @Override
    public void remove(List<String> expiredFileInfoIds) {
        for (String fileInfoId : expiredFileInfoIds) {
            Path filePath = getFilePath(fileInfoId);
            File file = filePath.toFile();
            if (file.exists()) {
                file.delete();
            }
        }
    }

    @Override
    public InputStream read(String fileInfoId) throws IOException, BlobAccessException {
        Path filePath = getFilePath(fileInfoId);
        File file = filePath.toFile();
        if (!file.exists()) {
            throw new FileNotFoundException(filePath.toString());
        }
        if (!file.canRead() || !file.isFile()) {
            throw new BlobAccessException("file not readable");
        }
        return new FileInputStream(file);
    }

    private Path getFilePath(String fileInfoId) {
        return Paths.get("uploads", fileInfoId);
    }

    //  TODO 写文件块、组合文件块、删除文件、删除文件块
}