package cc.ddrpa.tuskott.tus.provider.impl;

import cc.ddrpa.tuskott.tus.provider.FileInfo;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

public class SimpleFileInfo implements FileInfo, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String checksum;
    // 文件总体积（字节数）
    private Long uploadLength;
    // 稍后指定文件体积
    private Boolean uploadDeferLength;
    // 上传进度
    private Long uploadOffset;
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
    private String metadata;

    public SimpleFileInfo(String id, Long uploadLength, String metadata) {
        this.id = id;
        this.uploadLength = uploadLength;
        this.uploadDeferLength = false;
        this.uploadOffset = 0L;
        this.createTime = LocalDateTime.now();
        this.expireTime = LocalDateTime.now().plusDays(1L);
        this.metadata = metadata;
    }

    public SimpleFileInfo(String id, String metadata) {
        this.id = id;
        this.uploadLength = 0L;
        this.uploadDeferLength = true;
        this.uploadOffset = 0L;
        this.createTime = LocalDateTime.now();
        this.expireTime = LocalDateTime.now().plusDays(1L);
        this.metadata = metadata;
    }

    public void patch(long newUploadOffset) {
        this.uploadOffset = newUploadOffset;
    }

    public void uploadLength(Long uploadLength) {
        this.uploadLength = uploadLength;
        this.uploadDeferLength = false;
    }

    @Override
    public String id() {
        return id;
    }

    public String checksum() {
        return checksum;
    }

    @Override
    public Long uploadLength() {
        return uploadLength;
    }

    @Override
    public Boolean uploadDeferLength() {
        return uploadDeferLength;
    }

    @Override
    public Long uploadOffset() {
        return uploadOffset;
    }

    public LocalDateTime createTime() {
        return createTime;
    }

    @Override
    public LocalDateTime expireTime() {
        return expireTime;
    }

    @Override
    public Map<String, String> metadata() {
        if (StringUtils.hasText(metadata)) {
            Map<String, String> decodedMetadata = new HashMap<>();
            String[] pairs = metadata.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(" ");
                if (kv.length == 1) {
                    decodedMetadata.put(kv[0], null);
                } else if (kv.length >= 2) {
                    String value = new String(Base64.getDecoder().decode(kv[1]));
                    decodedMetadata.put(kv[0], value);
                }
            }
            return decodedMetadata;
        } else {
            return Map.of();
        }
    }
}