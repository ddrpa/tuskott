package cc.ddrpa.tuskott.tus.provider;

import cc.ddrpa.tuskott.tus.FileInfo;
import java.util.List;
import java.util.Optional;

public interface FileInfoProvider {

    FileInfo create(String fileInfoID, Optional<Long> uploadLength, Optional<String> metadata);

    FileInfo patch(String fileInfoID, long newUploadOffset);

    FileInfo head(String fileInfoID);

    FileInfo updateUploadLength(String fileInfoID, Long uploadLength);

    List<String> getExpiredFileInfoIds();

    void delete(List<String> expiredFileInfoIds);
}