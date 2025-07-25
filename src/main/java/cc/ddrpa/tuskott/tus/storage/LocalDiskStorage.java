package cc.ddrpa.tuskott.tus.storage;

import cc.ddrpa.tuskott.exception.BlobAccessException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/**
 * 将上传的文件块保存在本地磁盘上
 */
public class LocalDiskStorage implements Storage {

    private final Path storageDir;
    private final String storageDirAsStr;

    public LocalDiskStorage(Map<String, Object> configuration) throws IOException {
        storageDirAsStr = (String) configuration.getOrDefault("dir", "uploads");
        storageDir = Paths.get(storageDirAsStr);
        if (Files.notExists(storageDir)) {
            Files.createDirectories(storageDir);
        } else if (!Files.isDirectory(storageDir)) {
            throw new RuntimeException("Upload path is not a directory: " + storageDir);
        }
    }

    @Override
    public void create(String resourceId) throws BlobAccessException, IOException {
        Path filePath = buildFilePath(resourceId);
        if (Files.exists(filePath)) {
            throw new BlobAccessException(filePath + " already exists");
        }
        Files.createFile(filePath);
    }

    @Override
    public Long write(String resourceId, InputStream inputStream, Long uploadOffset)
            throws FileNotFoundException, BlobAccessException {
        Path filePath = accessFilePath(resourceId);
        long transferred = 0L;
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw")) {
            raf.seek(uploadOffset);
            byte[] buffer = new byte[1024];
            while (true) {
                int bytesRead;
                try {
                    bytesRead = inputStream.read(buffer);
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
            throw new BlobAccessException(e.getMessage());
        }
        return uploadOffset + transferred;
    }

    @Override
    public void remove(List<String> resourceIds) {
        for (String resourceId : resourceIds) {
            try {
                Files.delete(buildFilePath(resourceId));
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void remove(String resourceId) {
        try {
            Files.delete(buildFilePath(resourceId));
        } catch (IOException ignored) {
        }
    }

    @Override
    public InputStream streaming(String resourceId) throws IOException, BlobAccessException {
        Path filePath = accessFilePath(resourceId);
        return Files.newInputStream(filePath, StandardOpenOption.READ);
    }

    private Path buildFilePath(String resourceId) {
        return Paths.get(storageDirAsStr, resourceId);
    }

    private Path accessFilePath(String resourceId) throws FileNotFoundException, BlobAccessException {
        Path filePath = Paths.get(storageDirAsStr, resourceId);
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException(filePath + " already exists");
        }
        if (!Files.isReadable(filePath) || !Files.isWritable(filePath) || !Files.isRegularFile(filePath)) {
            throw new BlobAccessException(filePath + " is not readable or not a regular file");
        }
        return filePath;
    }
}