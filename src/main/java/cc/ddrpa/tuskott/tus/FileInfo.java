package cc.ddrpa.tuskott.tus;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FileInfo {

    private String id;
    //    private String fileHash;
    // 文件总体积（字节数）
    private Long uploadLength;
    // 稍后指定文件体积
    private Boolean uploadDeferLength;
    // 上传进度
    private Long uploadOffset;
    //    private List<String> chunkHashes;
//    private List<Boolean> chunkUploaded;
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
    private String metadata;
    private Map<String, String> decodedMetadata;

    public FileInfo(String id) {
        this.id = id;
        this.uploadOffset = 0L;
        this.createTime = LocalDateTime.now();
        this.expireTime = createTime.plusDays(1);
        this.uploadDeferLength = true;
    }

    public FileInfo updateUploadOffset(Long uploadOffset) {
        this.uploadOffset = uploadOffset;
        this.expireTime = LocalDateTime.now().plusDays(1);
        return this;
    }

    public FileInfo setUploadLength(Long uploadLength) {
        this.uploadLength = uploadLength;
        this.uploadDeferLength = false;
        this.expireTime = LocalDateTime.now().plusDays(1);
        return this;
    }

    public FileInfo setMetadata(String metadata) {
        this.metadata = metadata;
        this.decodedMetadata = parseMetadata(metadata);
        return this;
    }

    protected Map<String, String> parseMetadata(String metadata) {
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
    }
}