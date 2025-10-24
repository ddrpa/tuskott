package cc.ddrpa.tuskott.tus.resource;

import org.springframework.util.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class UploadResource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final LocalDateTime createTime;
    private final LocalDateTime expireTime;
    private final String metadata;
    private String checksum;
    // 文件总体积（字节数）
    private Long uploadLength;
    // 稍后指定文件体积
    private Boolean uploadDeferLength;
    // 上传进度
    private Long uploadOffset;

    public UploadResource(String id, LocalDateTime createTime, LocalDateTime expireTime, String metadata, String checksum, Long uploadLength, Boolean uploadDeferLength, Long uploadOffset) {
        this.id = id;
        this.createTime = createTime;
        this.expireTime = expireTime;
        this.metadata = metadata;
        this.checksum = checksum;
        this.uploadLength = uploadLength;
        this.uploadDeferLength = uploadDeferLength;
        this.uploadOffset = uploadOffset;
    }

    public UploadResource(String id, Long uploadLength, String metadata) {
        this.id = id;
        this.uploadLength = uploadLength;
        this.uploadDeferLength = false;
        this.uploadOffset = 0L;
        this.createTime = LocalDateTime.now();
        this.expireTime = LocalDateTime.now().plusDays(1L);
        this.metadata = metadata;
    }

    public UploadResource(String id, String metadata) {
        this.id = id;
        this.uploadLength = 0L;
        this.uploadDeferLength = true;
        this.uploadOffset = 0L;
        this.createTime = LocalDateTime.now();
        this.expireTime = LocalDateTime.now().plusDays(1L);
        this.metadata = metadata;
    }

    public UploadResource patch(long newUploadOffset) {
        this.uploadOffset = newUploadOffset;
        return this;
    }

    public UploadResource uploadLength(Long uploadLength) {
        this.uploadLength = uploadLength;
        this.uploadDeferLength = false;
        return this;
    }

    public Map<String, String> getMetadata() {
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

    public String getId() {
        return id;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public String getChecksum() {
        return checksum;
    }

    public Long getUploadLength() {
        return uploadLength;
    }

    public Boolean getUploadDeferLength() {
        return uploadDeferLength;
    }

    public Long getUploadOffset() {
        return uploadOffset;
    }

    @Override
    public String toString() {
        return "UploadResource{" +
                "id='" + id + '\'' +
                ", createTime=" + createTime +
                ", expireTime=" + expireTime +
                ", metadata='" + metadata + '\'' +
                ", checksum='" + checksum + '\'' +
                ", uploadLength=" + uploadLength +
                ", uploadDeferLength=" + uploadDeferLength +
                ", uploadOffset=" + uploadOffset +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UploadResource that = (UploadResource) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(createTime, that.createTime) &&
                Objects.equals(expireTime, that.expireTime) &&
                Objects.equals(metadata, that.metadata) &&
                Objects.equals(checksum, that.checksum) &&
                Objects.equals(uploadLength, that.uploadLength) &&
                Objects.equals(uploadDeferLength, that.uploadDeferLength) &&
                Objects.equals(uploadOffset, that.uploadOffset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, createTime, expireTime, metadata, checksum, uploadLength, uploadDeferLength, uploadOffset);
    }
}