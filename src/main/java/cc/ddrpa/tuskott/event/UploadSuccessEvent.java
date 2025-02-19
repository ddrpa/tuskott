package cc.ddrpa.tuskott.event;

import cc.ddrpa.tuskott.tus.provider.FileInfo;
import java.time.LocalDateTime;

public record UploadSuccessEvent (
    LocalDateTime eventTime,
    FileInfo fileInfo) {

    public UploadSuccessEvent(FileInfo fileInfo) {
        this(LocalDateTime.now(), fileInfo);
    }
}