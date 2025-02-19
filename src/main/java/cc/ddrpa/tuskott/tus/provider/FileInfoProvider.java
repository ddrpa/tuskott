package cc.ddrpa.tuskott.tus.provider;

import jakarta.annotation.Nullable;
import java.util.List;

public interface FileInfoProvider {

    FileInfo create(String fileInfoID, @Nullable Long uploadLength, @Nullable String metadata);

    FileInfo patch(String fileInfoID, long newUploadOffset);

    FileInfo head(String fileInfoID);

    FileInfo updateUploadLength(String fileInfoID, Long uploadLength);

    List<String> getExpiredFileInfoIds();

    void delete(List<String> fileInfoIds);
}