# Simple API Doc - AGENTS.md

## 1. 项目概览 (Project Overview)
Simple API Doc 是一个基于 Spring Boot 开发的轻量级、高性能的 API 文档管理与调试系统。它旨在为开发团队提供高效的 API 文档导入、查看、分享、定时同步及在线调试功能。

- **核心目标**: 提供一个简单、流畅、易部署的 API 文档服务，解决传统 Swagger UI 体验不佳、大型 Schema 渲染卡顿以及文档无法多端安全分享的问题。
- **主要仓库**: [fugary/simple-api-doc](https://github.com/fugary/simple-api-doc)

## 2. 技术栈 (Tech Stack)

### 后端 (simple-api-doc)
- **核心框架**: Spring Boot 2.7.x
- **运行基线**: JDK 11，保持较低版本运行门槛，避免引入不必要的高版本 JDK 要求
- **数据库**: H2 (模型/开发), MySQL (生产支持)
- **持久层**: MyBatis Plus, FlywayDB (数据库版本管理)
- **文档解析与生成**: Swagger Parser (用于解析 Swagger/OpenAPI 文档), OpenAPI Generator, Springdoc OpenAPI UI, Flexmark (用于解析/渲染 Markdown)
- **工具库**: Lombok, java-jwt (用于用户登录认证的 Token 管理), Apache HttpClient
- **模板引擎**: Freemarker

### 前端 (simple-api-doc-ui)
- **框架/构建**: Vue 3 + Vite 7
- **组件库**: Element Plus, @element-plus/icons-vue
- **编辑器**: Monaco Editor (@guolao/vue-monaco-editor, 用于查看及编辑请求/响应数据)
- **Markdown 编辑与渲染**: md-editor-v3, markdown-it, katex, mermaid (图表支持)
- **状态管理**: Pinia, pinia-plugin-persistedstate (持久化缓存)
- **路由**: Vue Router
- **工具库**: Split.js (界面分栏拖拽), Sortablejs (拖拽排序), Axios (网络请求), Lodash-es, VueUse, Vue I18n (国际化支持)

## 3. 核心功能 (Core Features)

| 功能模块 | 说明 |
| :--- | :--- |
| **API导入与同步** | 支持从本地 Swagger/OpenAPI 的 JSON/YAML 文件或 URL 导入文档，支持配置定时自动抓取。 |
| **Markdown 说明文档** | 深度集成 Markdown 格式，支持为 API 项目编写并展示说明文档，支持目录结构。 |
| **版本历史对比** | 自动记录 API 和 Markdown 文档的修改历史，集成在线 Diff 对比工具，支持版本回滚。 |
| **文档安全分享** | 支持生成加密分享链接，并可配置分享有效期与访问密码，便于安全便捷地对接第三方。 |
| **在线接口调试** | 内置在线调试与测试接口功能，支持解析粘贴的 cURL 命令并自动填充参数，支持预览多种格式（JSON/XML/Event-Stream）。 |
| **数据视图优化** | JSON 响应及 XML 响应（转换后）支持以易读的“表格”形式进行树状级联查看，支持排序及快捷操作。 |
| **用户与权限控制** | 细粒度的项目与分组管理，支持协作成员配置（可读/可写/管理员），保护文档数据安全。 |
| **UI/UX 体验** | 支持响应式布局、大屏与小屏自适应、暗黑/明亮主题切换动画、左侧菜单拖拽分栏（Split.js）、多标签面包屑管理。 |

## 4. 项目结构 (Project Structure)
- `/simple-api-doc`: 后端 Java 项目源码（Spring Boot）。
- `/simple-api-doc-ui`: 前端 Vue 3 项目源码。
- `/docs`: 项目文档及静态引导页。

## 5. 当前开发进度 (Current Status)
- [x] 分组与项目权限精细化控制：支持项目分组功能，优化项目及分组的用户权限关联，前端新增分组权限配置及编辑页面。
- [x] 多表单防重复提交优化：优化保存项目、分组、分享等数据时的判重逻辑，若内容无变化则不再重复保存数据库。
- [x] 系统安全性增强：增强调试接口传递 URL 验证，优化文档导出及生成代码的安全性控制。
- [x] 日志管理权限下放：将操作日志管理功能开放给普通用户，允许查看个人操作日志，并新增历史日志定时清理任务。
- [x] 主题切换与 UI/UX 优化：优化登录页面视觉设计，支持暗黑与明亮主题平滑切换动画；优化左侧树形菜单的拖动分栏、拖拽排序、弹窗遮罩及回到顶部/全屏按钮的交互设计。
- [x] API 响应多格式支持：支持 JSON 响应解析为树状表格形式查看，支持 XML 格式转 JSON 表格视图，支持 Event-Stream 实时推送数据预览。
- [x] AI 数据模拟与缓存管理：支持多引擎（openapi-sampler, Mock.js, json-schema-faker）生成模拟数据，并新增持久化缓存及后台管理页面。
- [x] AI 配置管理模块：支持在后台动态添加、编辑和切换 AI 接口配置（如 OpenAI, Anthropic 等），替代原有的硬编码配置方式，并支持多版本历史管理及一键回滚。
- [x] AI 智能生成数据模型：支持输入业务需求文本一键生成 PascalCase 模型名称、描述及完整 JSON Schema 结构，自动填入表单，并支持弹窗扩宽及多 AI 配置切换。
- [x] 全局变量提取规则关联项目接口：变量提取配置升级为支持选择项目 API 节点精准关联及匹配，具备 HTTP Method Tag/接口名富文本回显与自动适应 URL 变动能力，兼顾自定义模糊路径与正则通配匹配。
- [x] 数据模型 ContextMenu 与锁定状态：数据模型左侧列表支持与文档树一致的锁定状态图标及 Tooltip 提示，支持右键快捷菜单（ContextMenu）实现编辑、复制、锁定/解锁及删除操作。
- [x] 清空数据模型选项与安全防护：清空数据模型支持展示清空范围与锁定统计，新增“保留已锁定模型”选项（默认开启），提供实时动态影响反馈与全锁定防御拦截，杜绝误删关键模型。
- [x] AI 模型列表持久化缓存与自动恢复：通过 `AiConfigStore` 建立持久化模型缓存与双重特征索引（`configId` 与 `URL+APIKey`），实现 AI 配置、测试及业务中模型选择下拉列表的秒级自动恢复与按需刷新更新。
- [x] 多级 Markdown 文件夹与 ZIP 导入支持：新增 `MarkdownDocImporterImpl` 支持解析多级 Markdown 目录及 `.zip` 压缩包，自动过滤系统垃圾文件，支持 YAML Frontmatter 元数据提取、一级标题/序号排序智能解析及 README.md 根文档置顶展示。
- [x] 文件夹 folderCode 与改名同步保护机制：新增 `folderCode` 字段，实现文件夹标识与展示名称解耦；在多次重新导入/自动同步中智能保留用户自定义的文件夹、API 及 Markdown 文档名称，解决改名后同步产生重复目录或覆盖自定义名称的问题。

---
*Last Updated: 2026-08-27*

## 6. 项目规则 (Project Rules)
为了保证项目的开发的一致性和质量，AI 代理在协作时需遵循项目内置的规则：

详细规则请参考：[.agent/rules/rules.md](.agent/rules/rules.md)

主要包含：
1. **提交日志**: 生成提交日志时请使用 **中文**。
2. **前端开发**: 修改前端 JS、Vue 等文件时需通过 **ESLint** 验证。
3. **进度记录**: 每次提交后及时同步更新 [DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md)。
4. **架构同步**: 发生重大功能变更时更新 [AGENTS.md](AGENTS.md)。
