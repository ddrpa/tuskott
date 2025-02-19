package cc.ddrpa.tuskott.tus.provider.impl;

import cc.ddrpa.tuskott.tus.provider.FileInfo;
import cc.ddrpa.tuskott.tus.provider.FileInfoProvider;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class InMemoryFileInfoProvider implements FileInfoProvider {

    private static final Map<String, BasicFileInfo> fileInfoMap = new HashMap<>();

    @Override
    public BasicFileInfo create(String fileInfoID, Long uploadLength, String metadata) {
        BasicFileInfo fileInfo;
        if (Objects.nonNull(uploadLength)) {
            fileInfo = new BasicFileInfo(fileInfoID, uploadLength, metadata);
        } else {
            fileInfo = new BasicFileInfo(fileInfoID, metadata);
        }
        fileInfoMap.put(fileInfoID, fileInfo);
        return fileInfo;
    }

    @Override
    public BasicFileInfo patch(String fileInfoID, long newUploadOffset) {
        fileInfoMap.get(fileInfoID).patch(newUploadOffset);
        return fileInfoMap.get(fileInfoID);
    }

    @Override
    public BasicFileInfo head(String fileInfoID) {
        return fileInfoMap.get(fileInfoID);
    }

    @Override
    public BasicFileInfo updateUploadLength(String fileInfoID, Long uploadLength) {
        fileInfoMap.get(fileInfoID).uploadLength(uploadLength);
        return fileInfoMap.get(fileInfoID);
    }

    @Override
    public List<String> expire(Boolean instantRemove) {
        List<String> ids = fileInfoMap.values()
            .stream()
            .filter(fileInfo -> LocalDateTime.now().isBefore(fileInfo.expireTime()))
            .map(FileInfo::id)
            .toList();
        if (!ids.isEmpty() && instantRemove) {
            ids.forEach(fileInfoMap::remove);
        }
        return ids;
    }

    @Override
    public void complete(String fileInfoID, Boolean instantRemove) {
        if (instantRemove) {
            fileInfoMap.remove(fileInfoID);
        }
    }

    @Override
    public void remove(List<String> fileInfoIds) {
        fileInfoIds.forEach(fileInfoMap::remove);
    }
}