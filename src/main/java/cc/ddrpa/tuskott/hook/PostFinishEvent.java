package cc.ddrpa.tuskott.hook;

import cc.ddrpa.tuskott.tus.provider.FileInfo;
import java.time.LocalDateTime;

public record PostFinishEvent(
    LocalDateTime eventTime,
    FileInfo upload) {

    public PostFinishEvent(FileInfo upload) {
        this(LocalDateTime.now(), upload);
    }
}