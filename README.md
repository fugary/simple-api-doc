# simple-api-doc

### 文档网站

项目文档网站位于 [docs/index.html](docs/index.html)，英文入口位于 [docs/index_en.html](docs/index_en.html)。

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

### 发布新版本

推荐在 GitHub 的 **Actions → Build and Release → Run workflow** 中选择 `main`，输入正式版本号（如 `2.9.6` 或 `v2.9.6`）。工作流自动更新根目录与后端 `pom.xml`、前端 `package.json` 和 `package-lock.json`，校验版本一致性并完成前后端构建与后端测试，然后提交版本修改、创建 `v2.9.6` 标签并一起推送，最后发布 Docker 镜像、GitHub Release 和安装包。版本号目前仅支持 `主版本.次版本.修订版本`，不支持预发布后缀。

工作流使用内置 `GITHUB_TOKEN`（`contents: write`），仓库规则需允许它向 `main` 推送提交和创建 `v*` 标签；Docker Hub 继续使用仓库已有的 `DOCKERHUB_USERNAME` 和 `DOCKERHUB_TOKEN` Secrets。自动推送标签后在同一次运行中继续发布，无需额外配置个人访问令牌。

对于已经发布的小版本修复，可先把修复提交到 `main`，保持版本号不变，然后输入该版本号并勾选 **覆盖已有版本（使用当前 main 重新构建，仅限最新正式版本）**（默认关闭）。此模式要求目标标签已存在、是仓库最新正式版本，且 `main` 中的四个版本文件与输入一致；直接构建触发时的 `main` 提交，通过后更新原标签、替换同名安装包及版本镜像，更新说明会记录重新发布的时间、原提交、构建提交和本次修复。

覆盖发布需要允许更新目标 `v*` 标签，已启用不可变发布（immutable）的 Release 无法覆盖，工作流会提前拒绝。标签更新使用预期旧对象校验，构建期间 `main` 或目标标签发生变化会停止首次覆盖；更新前还会核对远端最新正式版本，若出现更高版本标签则停止覆盖。已更新标签后的失败重试复用原提交和发布记录。Git 标签、Docker 和 Release 无法跨服务原子更新，后续步骤失败时请重跑原任务完成发布。已有安装或容器不会自动升级，需要重新下载或拉取镜像；本地已有旧标签也需刷新。

也可以继续在本地执行 `new-version.bat 2.9.6`，检查并提交上述版本文件后，手动创建和推送 `v2.9.6` 标签触发发布。本地脚本只修改版本文件，不自动提交或打标签；标签触发的工作流会校验版本一致性，不修改源码版本。

若前后端构建或测试失败，自动发版不会推送版本提交或标签；如果标签已成功推送但后续发布失败，请在原运行中选择 **Re-run all jobs**。重试会复用该运行创建或更新的标签，不再次修改版本。未勾选覆盖选项时，新运行输入已经存在的版本会被拒绝。若提示 `main` 已变化，请从最新 `main` 重新发起运行。发布完成后，本地开发前请先拉取远程版本提交。

仅当发布版本不低于仓库已有正式版本标签时，才更新 Docker 的 `latest` 和 GitHub Release 的最新版本标记；补发或重试旧版本仍会发布对应版本镜像和安装包。

发布流程的本地回归检查：安装 Python 3.10+、PyYAML 和 Git（Windows 使用 Git Bash），执行 `python .github/scripts/test-release.py`。测试只操作临时本地仓库，不访问 GitHub 或 Docker Hub。

### 截图示例

![image-20241014134412817](https://git.mengqingpo.com:8888/fugary/blogpic/uploads/5a91e5e6361da3eb0ea11a1df7487919/image-20241014134412817.png)

参考文档： https://fugary.com/?p=636
