# HOW TO

获取服务端信息

```sh
curl -v -X OPTIONS localhost:8080/tus-playground/files
```

```
< HTTP/1.1 204 
< Tus-Resumable: 1.0.0
< Tus-Version: 1.0.0
< Tus-Extension: creation,creation-with-upload,termination
< Tus-Max-Size: 1073741824
< Allow: GET, HEAD, POST, PUT, DELETE, OPTIONS, PATCH
< Date: Sun, 19 Jan 2025 13:54:03 GMT
```

创建上传计划

```sh
curl -v -X POST localhost:8080/tus-playground/files
```

```
< HTTP/1.1 201 
< Location: http://localhost:8080/tus-playground/files/0b8b1cd5a5f44f78a097a03205969558
< Content-Length: 0
< Date: Sun, 19 Jan 2025 14:05:50 GMT
```

获取文件上传状态

```sh
curl -v -X HEAD http://localhost:8080/tus-playground/files/8a0e79da5df8427cbbdb12ce697dd715
```

```
< HTTP/1.1 200 
< Upload-Offset: 0
< Tus-Resumable: 1.0.0
< Date: Sun, 19 Jan 2025 14:08:39 GMT
```

创建一个 500M 的文件

```sh
# macOS
mkfile -n 500m fake-500m.file
```

上传文件，限速并且在上传完成之前终止进程

```sh
curl -v -X PATCH localhost:8080/tus-playground/files/0b8b1cd5a5f44f78a097a03205969558 \
    –-limit-rate 30m \
    -H 'Content-Type: application/offset+octet-stream' \
    -H 'Upload-Offset: 0' \
    --upload-file '/Users/yufan/WorkSpace/五链融合/tus-playground/uploads/fake-500m.file'
```

再次请求文件上传状态

```sh
curl -v -X HEAD http://localhost:8080/tus-playground/files/0b8b1cd5a5f44f78a097a03205969558
```

```
< HTTP/1.1 200
< Upload-Offset: 494615640
< Tus-Resumable: 1.0.0
< Date: Sun, 19 Jan 2025 14:28:39 GMT
```

续传

```sh
curl -v -X PATCH 'localhost:8080/tus-playground/files/0b8b1cd5a5f44f78a097a03205969558' \
    –-limit-rate 30m \
    -H 'Content-Type: application/offset+octet-stream' \
    -H 'Upload-Offset: 446750000' \
    --continue-at 446750000 \
    --upload-file '/Users/yufan/WorkSpace/五链融合/tus-playground/uploads/fake-500m.file'
```

检查文件 MD5

```sh
md5 fake-500m.file 0b8b1cd5a5f44f78a097a03205969558
> MD5 (fake-500m.file) = d8b61b2c0025919d5321461045c8226f
> MD5 (0b8b1cd5a5f44f78a097a03205969558) = d8b61b2c0025919d5321461045c8226f
```