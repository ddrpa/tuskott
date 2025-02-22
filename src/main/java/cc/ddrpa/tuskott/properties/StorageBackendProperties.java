package cc.ddrpa.tuskott.properties;

import lombok.Data;

@Data
public class StorageBackendProperties {

    // 存储后端提供者
    private String storeBackendProvider;
    // 基于本地磁盘的存储后端配置
    private LocalDiskProperties localDisk = new LocalDiskProperties();
}