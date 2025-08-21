# Tuskott AI Coding Reference

## Project Overview
**Artifact**: `cc.ddrpa.tuskott:tuskott-spring-boot-starter:0.0.3-SNAPSHOT`  
**Purpose**: Spring Boot Starter for TUS resumable upload protocol support  
**Java Version**: 17+  
**Spring Boot**: 3.5.4

## Essential Configuration Schema

### Core Configuration Properties
```yaml
tuskott:
  base-path: "/tus"                    # String - TUS endpoint base path - REQUIRED
  max-upload-length: 1073741824        # long - Max file size bytes (1GB default) - REQUIRED  
  max-chunk-size: 52428800             # long - Max chunk size bytes (50MB default) - REQUIRED
```

### Proxy Configuration
```yaml
tuskott:
  behind-proxy:
    enable: false                      # boolean - Enable proxy support - OPTIONAL (default: false)
    header: "X-Original-Request-Uri"   # String - Proxy header name - OPTIONAL (default shown)
```

### Protocol Extensions
```yaml
tuskott:
  extension:
    enable-creation: true              # boolean - Enable creation extension - OPTIONAL (default: true)
    enable-termination: true           # boolean - Enable termination extension - OPTIONAL (default: true)
```

### Component Providers
```yaml
tuskott:
  tracker:
    provider: "cc.ddrpa.tuskott.tus.resource.InMemoryUploadResourceTracker"  # String - FQCN - OPTIONAL
    config: {}                         # Map<String,Object> - Provider-specific config - OPTIONAL
  lock:
    provider: "cc.ddrpa.tuskott.tus.lock.InMemoryLockProvider"               # String - FQCN - OPTIONAL  
    config: {}                         # Map<String,Object> - Provider-specific config - OPTIONAL
  storage:
    provider: "cc.ddrpa.tuskott.tus.storage.LocalDiskStorage"                # String - FQCN - OPTIONAL
    config:
      dir: "uploads"                   # String - Storage directory - REQUIRED for LocalDiskStorage
```

## Dependency Declaration
```xml
<dependency>
  <groupId>cc.ddrpa.tuskott</groupId>
  <artifactId>tuskott-spring-boot-starter</artifactId>
  <version>0.0.3-SNAPSHOT</version>
</dependency>
```

## Key API Classes & Methods

### TuskottProcessor - Main Service Class
```java
// Constructor injection - Auto-configured
@Autowired TuskottProcessor tuskottProcessor;

// Core Methods
public UploadResource createUploadResourceOnServerSide(String metadata) 
    throws BlobAccessException, IOException                    // Server-side upload creation
public UploadResourceTracker getTracker()                      // Get resource tracker
public Storage getStorage()                                    // Get storage backend  
public LockProvider getLockProvider()                          // Get lock provider
public void registerCallBack(List<TuskottEventCallback> postCreate, 
                           List<TuskottEventCallback> postFinish, 
                           List<TuskottEventCallback> postTerminate) // Register callbacks
```

### TuskottProperties - Configuration Class
```java
// Property access methods - Auto-configured from application.yaml
public String getBasePath()                                    // Get endpoint base path
public long getMaxUploadLength()                               // Get max upload size  
public long getMaxChunkSize()                                  // Get max chunk size
public Extension getExtension()                                // Get extension config
public BehindProxy getBehindProxy()                            // Get proxy config
public StorageProperties getStorage()                          // Get storage config
```

### UploadResource - Upload Metadata
```java
// Key properties and methods
public String getId()                                          // Get unique upload ID
public Map<String, String> getMetadata()                       // Get decoded metadata map
public Long getUploadLength()                                  // Get total upload size
public Long getUploadOffset()                                  // Get current upload progress  
public LocalDateTime getExpireTime()                           // Get expiration time
public Boolean getUploadDeferLength()                          // Check if length deferred
```

## Event System - Callback Annotations

### Event Annotations
```java
import cc.ddrpa.tuskott.event.annotation.*;
import cc.ddrpa.tuskott.event.*;

@PostCreate     // Triggered after upload resource creation
@PostComplete   // Triggered after upload completion  
@PostTerminate  // Triggered after upload termination
```

### Event Handler Pattern
```java
@Component
public class UploadEventHandler {
    
    @PostCreate
    public void handleCreate(PostCreateEvent event) {
        UploadResource resource = event.getUploadResource();
        // Handle upload creation
    }
    
    @PostComplete  
    public void handleComplete(PostCompleteEvent event) {
        UploadResource resource = event.getUploadResource();
        Map<String, String> metadata = resource.getMetadata();
        String filename = metadata.get("filename");
        // Handle upload completion - file processing, moving, etc.
    }
    
    @PostTerminate
    public void handleTerminate(PostTerminateEvent event) {
        UploadResource resource = event.getUploadResource(); 
        // Handle upload termination - cleanup, logging, etc.
    }
}
```

## Client-Side JavaScript Integration
```javascript
// Using tus-js-client with tuskott endpoints
const upload = new tus.Upload(file, {
    endpoint: '/tus/files',                // basePath + '/files'
    retryDelays: [0, 1000, 3000, 5000],
    chunkSize: 52428800,                   // Match max-chunk-size
    metadata: {
        filename: file.name,
        filetype: file.type
    },
    onProgress: (bytesUploaded, bytesTotal) => {
        // Progress callback
    },
    onSuccess: () => {
        // Success callback  
    }
});
upload.start();
```

## Storage Access Pattern
```java
@PostComplete
public void processUploadedFile(PostCompleteEvent event) {
    UploadResource resource = event.getUploadResource();
    String uploadId = resource.getId();
    
    // Access file through storage
    try (InputStream inputStream = tuskottProcessor.getStorage().streaming(uploadId);
         OutputStream outputStream = new FileOutputStream("/target/path")) {
        inputStream.transferTo(outputStream);
    } catch (Exception e) {
        // Handle error
    }
}
```

## Default Component Implementations

### Storage: LocalDiskStorage
- **Class**: `cc.ddrpa.tuskott.tus.storage.LocalDiskStorage`
- **Config**: `dir` - String - Directory path for file storage
- **Behavior**: Stores files in specified directory, creates if not exists

### Tracker: InMemoryUploadResourceTracker  
- **Class**: `cc.ddrpa.tuskott.tus.resource.InMemoryUploadResourceTracker`
- **Config**: None
- **Behavior**: In-memory upload metadata storage, NO auto-cleanup
- **Note**: Manual cleanup required via `filter()` method for expired uploads

### Lock: InMemoryLockProvider
- **Class**: `cc.ddrpa.tuskott.tus.lock.InMemoryLockProvider`  
- **Config**: None
- **Behavior**: In-memory locking for concurrent upload protection

## TUS Protocol Endpoints (Auto-registered)
- `OPTIONS {base-path}/files` - Server capabilities
- `POST {base-path}/files` - Create upload (if creation enabled)
- `HEAD {base-path}/files/{resource}` - Get upload progress
- `PATCH {base-path}/files/{resource}` - Upload file chunks
- `DELETE {base-path}/files/{resource}` - Terminate upload (if termination enabled)

## Configuration Best Practices

### Production Settings
```yaml
tuskott:
  max-upload-length: 5368709120        # 5GB for large files
  max-chunk-size: 104857600            # 100MB for better performance
  behind-proxy:
    enable: true                       # Enable if behind nginx/apache
    header: "X-Forwarded-Host"         # Adjust based on proxy
  extension:
    enable-termination: true           # Allow cleanup
  storage:
    config:
      dir: "/var/uploads"              # Absolute path recommended
```

### Development Settings  
```yaml
tuskott:
  base-path: "/api/upload"             # Custom endpoint path
  max-upload-length: 1073741824        # 1GB sufficient for dev
  max-chunk-size: 10485760             # 10MB for testing
  behind-proxy:
    enable: false                      # Direct access in dev
```

## Error Handling Patterns
```java
@PostComplete
public void processFile(PostCompleteEvent event) {
    try {
        UploadResource resource = event.getUploadResource();
        // Process file
    } catch (BlobAccessException e) {
        // Storage access failed
    } catch (IOException e) {
        // IO operation failed  
    } catch (Exception e) {
        // Generic error handling
    }
}
```

## Custom Provider Implementation
```java
// Custom storage provider example
@Component
public class CustomStorage implements Storage {
    @Override
    public void create(String resourceId) throws BlobAccessException {
        // Implementation
    }
    
    @Override 
    public Long write(String resourceId, InputStream inputStream, Long offset) 
            throws BlobAccessException {
        // Implementation
    }
    
    // Other required methods...
}

// Configuration to use custom provider
tuskott:
  storage:
    provider: "com.example.CustomStorage"
    config:
      customProperty: "value"
```

## Memory Management Notes
- `InMemoryUploadResourceTracker`: No auto-cleanup - requires manual cleanup
- Default expiration: 24 hours from creation
- Manual cleanup pattern:
```java
@Scheduled(fixedRate = 3600000) // Every hour
public void cleanupExpiredUploads() {
    tuskottProcessor.getTracker().filter(resource -> 
        resource.getExpireTime().isBefore(LocalDateTime.now()))
        .forEach(resource -> tuskottProcessor.getTracker().remove(resource.getId()));
}
```

## Integration Checklist
- [ ] Add dependency to pom.xml/build.gradle
- [ ] Configure `tuskott.base-path` in application.yaml
- [ ] Configure storage directory if using LocalDiskStorage  
- [ ] Set appropriate file size limits
- [ ] Implement event handlers for file processing
- [ ] Configure proxy settings if behind reverse proxy
- [ ] Set up manual cleanup for InMemoryUploadResourceTracker
- [ ] Configure client-side library with matching endpoints