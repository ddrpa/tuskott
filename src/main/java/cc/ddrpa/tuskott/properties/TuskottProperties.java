package cc.ddrpa.tuskott.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "tuskott")
public class TuskottProperties {

    // 决定 tuskott 在哪个路径下创建服务端点
    private String basePath = "/tus";
    // 允许上传的最大文件体积，默认 1GB
    private long maxUploadLength = 1_073_741_824L;
    // 单次允许上传，默认 50MB
    private long maxChunkSize = 52_428_800L;
    // 反向代理支持
    private BehindProxy behindProxy = new BehindProxy();
    // 扩展功能
    private Extension extension = new Extension();
    // 存储上传计划
    private UploadResourceTrackerProperties tracker = new UploadResourceTrackerProperties();
    // 提供锁
    private LockProviderProperties lock = new LockProviderProperties();
    // 存储后端配置
    private StorageProperties storage = new StorageProperties();

    @Data
    public static class Extension {
        private boolean enableCreation = true;
        private boolean enableTermination = true;
    }

    @Data
    public static class BehindProxy {
        // 是否部署在代理服务后
        private boolean enable = true;
        // 从指定请求头中识别实际请求地址
        private String header = "X-Original-Request-Uri";
    }

    @Data
    public static class LockProviderProperties {
        private String provider = "cc.ddrpa.tuskott.tus.lock.InMemoryLockProvider";
        // config 配置如何要看具体实现
        private Map<String, Object> config = Collections.emptyMap();
    }

    @Data
    public static class UploadResourceTrackerProperties {
        private String provider = "cc.ddrpa.tuskott.tus.resource.InMemoryUploadResourceTracker";
        // config 配置如何要看具体实现
        private Map<String, Object> config = Collections.emptyMap();
    }

    @Data
    public static class StorageProperties {
        private String provider = "cc.ddrpa.tuskott.tus.storage.LocalDiskStorage";
        private Map<String, Object> config = Collections.emptyMap();
    }
}