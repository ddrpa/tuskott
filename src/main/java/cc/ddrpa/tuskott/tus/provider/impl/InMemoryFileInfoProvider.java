package cc.ddrpa.tuskott.tus.provider.impl;

import cc.ddrpa.tuskott.tus.FileInfo;
import cc.ddrpa.tuskott.tus.provider.FileInfoProvider;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryFileInfoProvider implements FileInfoProvider {

    private static final Map<String, FileInfo> fileInfoMap = new HashMap<>();

    @Override
    public FileInfo create(String fileInfoID, Optional<Long> uploadLength,
        Optional<String> metadata) {
        FileInfo fileInfo = new FileInfo(fileInfoID);
        uploadLength.ifPresent(fileInfo::setUploadLength);
        metadata.ifPresent(fileInfo::setMetadata);
        fileInfoMap.put(fileInfoID, fileInfo);
        return fileInfo;
    }

    @Override
    public FileInfo patch(String fileInfoID, long newUploadOffset) {
        fileInfoMap.get(fileInfoID).updateUploadOffset(newUploadOffset);
        return fileInfoMap.get(fileInfoID);
    }

    @Override
    public FileInfo head(String fileInfoID) {
        return fileInfoMap.get(fileInfoID);
    }

    @Override
    public FileInfo updateUploadLength(String fileInfoID, Long uploadLength) {
        fileInfoMap.get(fileInfoID).setUploadLength(uploadLength);
        return fileInfoMap.get(fileInfoID);
    }

    @Override
    public List<String> getExpiredFileInfoIds() {
        return fileInfoMap.values()
            .stream()
            .filter(fileInfo -> LocalDateTime.now().isBefore(fileInfo.getExpireTime()))
            .map(FileInfo::getId)
            .toList();
    }

    @Override
    public void delete(List<String> expiredFileInfoIds) {
        expiredFileInfoIds.forEach(fileInfoMap::remove);
    }
}