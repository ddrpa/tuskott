package cc.ddrpa.tuskott.tus.provider;

import java.time.LocalDateTime;
import java.util.Map;

public interface FileInfo {

    String id();

    Long uploadLength();

    Boolean uploadDeferLength();

    Long uploadOffset();

    LocalDateTime expireTime();

    Map<String, String> metadata();
}