package cc.ddrpa.tuskott.hook;

import cc.ddrpa.tuskott.tus.provider.FileInfo;
import java.time.LocalDateTime;

public record PostCreateEvent(
    LocalDateTime eventTime,
    FileInfo upload
) {

}