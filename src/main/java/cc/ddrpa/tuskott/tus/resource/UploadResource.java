package cc.ddrpa.tuskott.tus.resource;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.util.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@ToString
@EqualsAndHashCode(callSuper = false)
public class UploadResource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    private final String id;
    @Getter
    private final LocalDateTime createTime;
    @Getter
    private final LocalDateTime expireTime;
    private final String metadata;
    @Getter
    private String checksum;
    // 文件总体积（字节数）
    @Getter
    private Long uploadLength;
    // 稍后指定文件体积
    @Getter
    private Boolean uploadDeferLength;
    // 上传进度
    @Getter
    private Long uploadOffset;

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
}