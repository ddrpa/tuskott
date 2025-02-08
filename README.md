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

```xml
<dependency>
  <groupId>cc.ddrpa.tuskott</groupId>
  <artifactId>tuskott-spring-boot-starter</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

用户在 POM 中引入项目后，只需要通过 `application.yaml` 声明一个端点路径，tuskott 会在这个路径下创建一系列接口，之后就可以使用任意 tus 客户端进行上传。

默认情况下 tuskott 将上传的文件保存在本地磁盘，使用内存中的数据结构保存进行中的上传计划信息和锁状态，这是为了保持开箱即用的能力。用户可以替换为自己的持久化、分布式的解决方案。

文件上传成功等事件发生时，tuskott 会异步调用 `@OnUploadSuccess` 等注解修饰的方法发送通知，允许使用者实现更多的业务功能。

## 一个例子

[HOW TO](HOWTO.md)