package cc.ddrpa.tuskott.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

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

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public long getMaxUploadLength() {
        return maxUploadLength;
    }

    public void setMaxUploadLength(long maxUploadLength) {
        this.maxUploadLength = maxUploadLength;
    }

    public long getMaxChunkSize() {
        return maxChunkSize;
    }

    public void setMaxChunkSize(long maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }

    public BehindProxy getBehindProxy() {
        return behindProxy;
    }

    public void setBehindProxy(BehindProxy behindProxy) {
        this.behindProxy = behindProxy;
    }

    public Extension getExtension() {
        return extension;
    }

    public void setExtension(Extension extension) {
        this.extension = extension;
    }

    public UploadResourceTrackerProperties getTracker() {
        return tracker;
    }

    public void setTracker(UploadResourceTrackerProperties tracker) {
        this.tracker = tracker;
    }

    public LockProviderProperties getLock() {
        return lock;
    }

    public void setLock(LockProviderProperties lock) {
        this.lock = lock;
    }

    public StorageProperties getStorage() {
        return storage;
    }

    public void setStorage(StorageProperties storage) {
        this.storage = storage;
    }

    public static class Extension {
        private boolean enableCreation = true;
        private boolean enableTermination = true;

        public boolean isEnableCreation() {
            return enableCreation;
        }

        public void setEnableCreation(boolean enableCreation) {
            this.enableCreation = enableCreation;
        }

        public boolean isEnableTermination() {
            return enableTermination;
        }

        public void setEnableTermination(boolean enableTermination) {
            this.enableTermination = enableTermination;
        }
    }

    public static class BehindProxy {
        // 是否部署在代理服务后
        private boolean enable = true;
        // 从指定请求头中识别实际请求地址
        private String header = "X-Original-Request-Uri";

        public boolean isEnable() {
            return enable;
        }

        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        public String getHeader() {
            return header;
        }

        public void setHeader(String header) {
            this.header = header;
        }
    }

    public static class LockProviderProperties {
        private String provider = "cc.ddrpa.tuskott.tus.lock.InMemoryLockProvider";
        // config 配置如何要看具体实现
        private Map<String, Object> config = Collections.emptyMap();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public Map<String, Object> getConfig() {
            return config;
        }

        public void setConfig(Map<String, Object> config) {
            this.config = config;
        }
    }

    public static class UploadResourceTrackerProperties {
        private String provider = "cc.ddrpa.tuskott.tus.resource.InMemoryUploadResourceTracker";
        // config 配置如何要看具体实现
        private Map<String, Object> config = Collections.emptyMap();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public Map<String, Object> getConfig() {
            return config;
        }

        public void setConfig(Map<String, Object> config) {
            this.config = config;
        }
    }

    public static class StorageProperties {
        private String provider = "cc.ddrpa.tuskott.tus.storage.LocalDiskStorage";
        private Map<String, Object> config = Collections.emptyMap();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public Map<String, Object> getConfig() {
            return config;
        }

        public void setConfig(Map<String, Object> config) {
            this.config = config;
        }
    }
}