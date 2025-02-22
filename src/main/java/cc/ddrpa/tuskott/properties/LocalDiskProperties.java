package cc.ddrpa.tuskott.properties;

import lombok.Data;

@Data
public class LocalDiskProperties {

    // 临时存储目录
    private String uploadDir = "uploads";
}