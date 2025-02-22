package cc.ddrpa.tuskott.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "tuskott")
public class TuskottProperties {

    // 接口路径
    private String basePath = "/tus";
    // 允许上传的最大文件体积，默认 1GB
    private long maxBlobSize = 1_073_741_824L;
    // 单次允许上传，默认 50MB
    private long maxUpload = 52_428_800L;
    // 是否部署在代理服务后
    private Boolean behindProxy = true;
    // 从指定请求头中识别实际请求地址
    private String uriHeaderName = "X-Original-Request-Uri";
    // 存储上传计划
    private String fileInfoProvider;
    // 提供锁
    private String lockProvider;
    // 存储后端配置
    private StorageBackendProperties storageBackend = new StorageBackendProperties();
}