# tuskott: A Spring Boot Starter that bring tus support

tuskott 是一个开发中的 Spring Boot Starter，目前实现了 [tus 协议](https://tus.io/protocols/resumable-upload) 中规定的大部分功能：

- 核心协议
    - [x] 查询上传进度
    - [x] 断点续传
- 扩展功能
    - [x] 创建上传计划
    - [x] 首次上传创建计划
    - [x] 上传终止
    - [ ] 分片上传与文件合并
    - [x] 文件校验
    - [x] 过期

项目仍在开发中，目前在 SNAPSHOT 仓库中提供了 `0.0.3-SNAPSHOT` 版本（注意 `0.0.3-SNAPSHOT` 版本在 API 和配置命名上和之前有所区别）

```xml
<dependency>
  <groupId>cc.ddrpa.tuskott</groupId>
  <artifactId>tuskott-spring-boot-starter</artifactId>
  <version>0.0.3-SNAPSHOT</version>
</dependency>
```

# HowTo

如下展示了 `application.yaml` 配置的默认值：

```yaml
tuskott:
  # tuskott 在何处创建 tus 服务端点
  base-path: /tus
  # 允许上传的最大文件大小，单位为字节，默认为 1GB
  max-upload-length: 1073741824
  # 允许上传的最大分片大小，单位为字节，默认为 50MB
  max-chunk-size: 52428800
  behind-proxy:
    # 服务是否部署在反向代理后面，影响 tuskott 分配上传地址的行为
    enable: false
#    header: 'X-Original-Request-Uri'
  # 是否启用 tus 协议的扩展功能
  extension:
    enable-creation: true
    enable-termination: true
  # 允许用户替换自己的 UploadResourceTracker 实现
  tracker:
    provider: 'cc.ddrpa.tuskott.tus.resource.InMemoryUploadResourceTracker'
  # 允许用户替换自己的 LockProvider 实现
  lock:
    provider: 'cc.ddrpa.tuskott.tus.lock.InMemoryLockProvider'
  # 允许用户替换自己的 Storage 实现
  storage:
    provider: 'cc.ddrpa.tuskott.tus.storage.LocalDiskStorage'
    config:
      # 文件将被存储到工作目录的 uploads 子目录下
      dir: 'uploads'
```

## 构成

Tuskott 提供了一些默认的组件，这些组件并不是为分布式环境设计的，也没有持久化设计，但可以满足大多数单机应用的需求。

用户可以直接使用这些组件，也可以按需替换为自己的实现。

- UploadResourceTracker 负责管理上传计划信息，在 `InMemoryUploadResourceTracker` 中，上传文件的信息保存在一个 Map 结构中
- LockProvider 负责管理锁，在 `InMemoryLockProvider` 中，锁信息保存在内存中
- Storage 负责管理文件存储，`LocalDiskStorage` 将上传的文件保存在本地磁盘指定的目录中

注意 `UploadResourceTracker` 接口并未要求组件实现自动清理机制，因此在使用 `InMemoryUploadResourceTracker` 时用户需定期调用 `filter` 找出过期的上传计划并手动清理。

## 从 Web 页面上传文件

可以使用大部分 tus 客户端库，以 JavaScript 的 [tus-js-client](https://github.com/tus/tus-js-client) 为例，当设置 `tuskott.base-path = '/tus'` 时，在前端代码中可以这样创建上传实例：

```javascript
let upload = new tus.Upload(file, {
    endpoint: '/tus/files',
    retryDelays: [0, 1000, 3000, 5000],
    chunkSize: currentChunkSize,
    metadata: {
        filename: file.name,
        filetype: file.type
    }
});
upload.start();
```

为了提升用户体验，你可以在前端代码中监听上传进度事件，更新进度条等 UI 元素。

## 事件回调

Tuskott 提供了一系列事件回调，允许用户在上传的不同节点注册需执行的方法。

- `PostCreateEvent` 创建 Upload Resource 后触发
- `PostCompleteEvent` 上传完成后触发
- `PostTerminateEvent` 上传终止后触发

如下代码展示了上传完成后触发转存的操作：

```java
@PostComplete
public void uploadSuccessEvent(PostCompleteEvent event) {
    UploadResource uploadResource = event.getUploadResource();
    Map<String, String> metadata = uploadResource.metadata();
    String originalFilename = metadata.get("filename");
    try(OutputStream ous = new FileOutputStream("/home/dandier/Temp/saved_" + originalFilename);
        InputStream ins = tuskottProcessor.getStorage().streaming(uploadResource.id())) {
        ins.transferTo(ous);
    } catch (Exception e) {
        logger.error("Failed to save uploaded file", e);
    }
}
```
