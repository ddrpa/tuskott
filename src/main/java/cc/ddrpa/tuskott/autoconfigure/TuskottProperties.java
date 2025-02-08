package cc.ddrpa.tuskott.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "tuskott")
public class TuskottProperties {

    // 接口相对路径，默认 /tus，接口端点为 /tus/files
    private String endpoint = "/tus";
    // 允许上传的最大文件体积，默认 1GB
    private long maxStoreSize = 1_073_741_824L;
    // 单次允许上传，默认 50MB
    private long maxRequestSize = 52_428_800L;
}