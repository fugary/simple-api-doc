# simple-api-doc

最近使用新`OpenAPI`接口时发现自带的`SwaggerUI`界面不是很友好，一些开源的库，如`Knife4j`使用虽然很方便，不过对于比较大的`schema`支持不好，容易出现卡顿，目前做的比较好的是`ApiFox`，不过不能同步`Markdown`文件，而且私有化部署需要另外收费。

其实`API`文档服务比较简单，没有太多功能，因此抽空自己开发一个简单的API文档系统

### 功能介绍

由一个`Java`后端和`Vue3`前端组成，主要功能如下：

1. 从`Swagger`或者`OpenAPI`的文档（`json/yaml`)文件或`URL`路径导入文档系统
2. 支持查看`Markdown`文档和`API`文档字段等
3. 支持新增`Markdown`文档，方便对`OpenAPI`文档做一些附加说明等
4. 支持分享链接和密码查看，支持分享有效期
5. 支持定时从指定`URL`抓取文档数据
6. 支持在线调试接口请求
7. 支持多级文件夹展示`API`文档

### 安装方式

找到https://github.com/fugary/simple-api-doc/releases
下载最新版本后解压，进入`bin`目录

点击`start.bat`即可启动

### Docker安装

`docker`运行比较简单，只要已经安装好`docker`，直接使用命令就可以自动拉去镜像并运行了

```
docker run -p 9089:9089 fugary/simple-api-doc:latest
```

启动后可以进入登录页面：http://localhost:9089/
默认账号密码：`admin/12345678`

### 截图示例

![image-20241014134412817](https://git.mengqingpo.com:8888/fugary/blogpic/uploads/5a91e5e6361da3eb0ea11a1df7487919/image-20241014134412817.png)

参考文档： https://fugary.com/?p=636
