# 开发进度日志 (Detailed Development Log)

本文档完整记录了 `simple-api-doc` 项目的详细开发历程、功能迭代及维护记录。

### 2026-08
- **opt**: [2026-08-03] 优化及精简全局环境 URL 同步与调试参数合并代码逻辑：1. 抽取通用的 `NOT_SAVED_KEYS` 与 `mergeSavedParamTarget` 封装工具方法，消除 `ApiDocViewer.vue` 与 `ApiDocPreviewService.js` 中 10 余行重复的参数预处理合并逻辑；2. 精简 `ApiDocAuthorizationWindow.vue` 与 `ApiDocViewer.vue` 中无用的组件属性透传；3. 保证主详情页、接口调试弹窗、登录调试弹窗全链路 `targetUrl` 的响应式联动与数据单例存储；全量通过 ESLint 规范校验。
- **bug**: [2026-08-03] 修复 API 树形菜单右键弹出上下文菜单后再次右键点击其他节点无反应及无法更新位置的问题：在 `ApiFolderTreeViewer.vue` 的 `handleNodeContextMenu` 中，利用 `showContextMenu` 状态控制上下文菜单 DOM 重新挂载，解决 Element Plus 下拉组件 Popper 位置缓存导致的右键点击其他位置菜单不移动/无法展开新节点菜单的 Bug；右键点击节点仅弹出菜单且不主动改变当前已选中的文档树节点（避免切换当前查看的接口），并在展开节点右侧 `more-actions` 菜单时自动隐藏关闭右键上下文菜单，防止弹窗菜单重叠；全量通过 ESLint 规范校验。
- **opt**: [2026-08-03] 修复前端缺失及未匹配的 i18n 资源 Key：
  1. **补全缺失 Key**：补全前端代码中引用但未在 locale 中定义的 6 个 Key：`common.msg.dataNotFound`（'未找到相关数据'）、`common.label.unlimited`（'不限'）、`common.msg.saveFailed`（'保存失败。'）、`common.msg.deleteFailed`（'删除失败。'）、`common.msg.operationFailed`（'操作失败。'）、`common.msg.unknownError`（'未知错误'）；
  2. **对齐中英文语言包**：修复中英文语言包不对称问题，补全 `api.label.corsMode`（中文 '跨域模式'）及 `menu.label.menuManagement`（英文 'Menu Management'），并清理冗余未引用的 `menu.label.menuOperation`；
  3. **自动化校验**：运行 i18n 校验脚本，确认全部 534 个 Key 中英文 100% 对齐且代码引用零缺失；
- **feat**: [2026-08-03] 后端纯集中实现文档分享模式下「登录接口」配置自动过滤与防泄露：
  1. **后端工具类实现**：在 `SimpleModelUtils.java` 中新增 `filterGroupConfigLoginApis(String groupConfig, Set<Integer> allowedDocIds)` 方法，使用 Jackson 对 `groupConfig` JSON 的 `loginApiConfigs` 数组基于分享授权 `shareDocIds` 进行精准过滤；
  2. **控制器响应过滤**：在 `SimpleShareController.java` 的 `/shares/loadProject/{shareId}` 与 `/shares/loadShareDoc/{shareId}/{docId}` 接口响应组装中，若分享配置了具体 `shareDocIds` 集合，自动对 `groupConfig` 进行接口级过滤，保证未授权的登录接口数据绝不离开服务器；
  3. **前端零修改**：前端代码恢复 100% 原始状态，无需任何前端改动，按原逻辑解析 `groupConfig.loginApiConfigs` 即可得到已授权的登录接口；
  4. 全量通过 Maven `test-compile` 构建校验。

- **opt**: [2026-08-04] 深度审查并精简前后端分享配置合并与过滤代码逻辑，消除不必要的重复修改：1. 针对前端 `SimpleShareApi.js` 中复杂的 `mergeEnvConfigs` 逻辑，因后端 `SimpleShareController.java` 已利用 `SimpleModelUtils.filterShareProject` 统一完成了环境配置（envContent）与登录接口（loginApiConfigs）的安全过滤，前端无需进行二次循环交叉比对，将其大幅度简化，直接信任后端已过滤数据，彻底消除前后端逻辑重叠与冗余代码；2. 审查 `ApiDocPreviewService.js` 中 `paramTarget` 数据对象的构建逻辑，确保它始终保持轻量化，绝不引入 `apiShare` 等全量分享配置大对象，保障内存安全与交互流畅；3. 后端集中处理 JSON 解析转换（如 `filterShareEnvContent`），所有核心分享鉴权及敏感数据截断统一收口于 Controller 输出前，使架构更加清晰优雅；通过前端 ESLint 与后端 `test-compile` 全量检验。

### 2026-07
- **opt**: [2026-07-31] 深度代码审查与 Vue 组件级重构优化：1. 在 `ApiEnvParams.vue` 中发现并抽取高度重复的 `<TreeConfigWindow>` 树形弹窗 DOM 结构，将其统一收口为单一弹窗实例，通过 `treeConfigContext` 状态与动态 `:single-select`、`:title` 属性动态复用于“登录接口选择”与“提取规则接口选择”两大场景，成功移除近 40 行重复冗余的 template 代码；2. 将重复的 `groupConfig` JSON 反序列化逻辑抽象提取为独立的 `parseGroupConfig` 工具函数，减少业务逻辑中的心智负担与重复代码，大幅提升代码优雅度、可读性与可维护性；3. 通过 ESLint 静态代码检查。
- **opt**: [2026-07-31] 优化全局变量提取规则表单校验根因修复、一键清空与树选择同步：1. 彻底解决保存报错阻断（最终根因修复）：定位到 Element Plus `el-table` 在渲染布局与列宽度计算时，会使用空对象 `{}` 渲染虚拟测量行，调用 `item.extractRules.indexOf({})` 必然返回 `-1`，导致 `<el-form-item>` 误将 `envParams.0.extractRules.-1.apiPath` 注册进 `el-form` 校验表并触发 `fieldValue: undefined` 报错阻断保存；通过在 `matchPathSlot` 的 `<el-form-item>` 上添加 `v-if="item.extractRules && item.extractRules.indexOf(rule) !== -1"` 条件过滤，确保仅真实数据行渲染校验项，彻底根除了该问题；2. 解决清空需要点击两次的问题：在 `switchToCustomPath` 清空逻辑中同步重置 `rule.apiPath = ''`，实现一键同时清空已绑定接口与路径文本；3. 解决清空后重新打开选择树残留高亮节点问题：在 `TreeCheckConfig.vue` 中为 `selectedKeys` 添加深度 `watch` 监听，当绑定的已选 Array 为空时自动触发 `treeRef.value.setCurrentKey(null)`，保证弹窗高亮状态与底层绑定数据 100% 同步；4. 通过全量 ESLint 规范校验。
- **feat**: [2026-07-31] 全局变量与提取配置支持选择项目接口匹配：1. 升级 `ApiEnvParams.vue` 中的变量提取规则配置，在“匹配路径”与“正则匹配”列引入模式切换与树状选择器，支持直接通过 900px `TreeConfigWindow` 选择当前项目的具体接口节点，自动带出 HTTP Method Tag（如 POST/GET）、接口名称与 URL 路径；同时提供“自定义路径”与“更换接口”按钮，保持对模糊路径与正则匹配的完全兼容；2. 升级 `ApiDocPreviewService.js` 中的 `isPathMatch` 与 `extractVariables` 匹配引擎，增加 `currentApiId` 参数与 `rule.apiId` 判定，使已绑定接口的提取规则在接口 URL 修改后依然精准生效；3. 在 `ApiDocRequestPreview.vue` 发送调试请求时传入当前调试接口 ID (`apiDocDetail.value.id`)；4. 补充 `api_cn.js` 与 `api_en.js` 国际化文案，通过全量 ESLint 规范验证。
- **bug**: [2026-07-31] 修复 API 树形菜单右键弹出上下文菜单后再次右键点击其他节点无反应的问题：在 `ApiFolderTreeViewer.vue` 的 `handleNodeContextMenu` 中，在更新弹出位置及展开新菜单前显式调用 `contextMenuDropdownRef.value?.handleClose?.()` 关闭已有菜单，确保 Element Plus 下拉组件重置状态并在新节点位置重新定位弹出；全量通过 ESLint 校验。
- **feat**: [2026-07-31] 升级请求调试与认证弹窗中的登录接口交互：1. 抽取 `openLoginApiDebug` 与 `parseLoginApiConfigs` 通用服务方法至 `ApiDocPreviewService.js`，消除代码重复；2. 提取公共组件 `ApiDocLoginApiDropdown.vue` 封装“登录接口”多选下拉/单选链接逻辑与 `<ApiMethodTag>`、接口名称、URL 路径的富文本呈现，彻底消除原有代码中的重复 DOM 结构；3. 升级接口调试请求栏（`ApiRequestFormReq.vue`）的“登录接口”控件，无缝复用上述精简组件；4. 在认证弹窗（`ApiDocAuthorizationWindow.vue`）顶栏右侧新增“登录接口”快捷入口，在配置有登录接口时允许直接触发登录，快速获取与刷新 Token；5. 通过 ESLint 代码规范全量验证，保证代码极致精简与高优雅度。
- **feat**: [2026-07-31] 全局变量与提取配置新增变量提取规则及登录接口多选拖拽排序功能：1. 升级通用表格表单组件 `CommonTableForm`（[index.vue](file:///d:/MyCodes/OtherCodes/simple-api-doc-parent/simple-api-doc-ui/src/components/common-table-form/index.vue)），新增 `sortable` 属性支持；为表格绑定 `useRenderKey()` 生成的唯一 `:row-key="rowKey"`，解决因为缺少稳定行 Key 导致 `el-table` 在 Sortable.js 拖拽完成后按默认 index 重新渲染而产生的“松手视觉弹回”问题；为拖拽图标列添加与表单项完全一致的 32px 弹性高度容器，解决因为表单项预留错误提示空间导致拖拽图标无法与右侧输入框水平居中对齐的问题；2. 升级 `useSortableParams` Hook（[CommonHooks.js](file:///d:/MyCodes/OtherCodes/simple-api-doc-parent/simple-api-doc-ui/src/hooks/CommonHooks.js)），自动识别并兼容 `el-table` 的 `tbody` 节点，使表格行支持 Sortable.js 拖拽排序；3. 在 `ApiEnvParams.vue` 中为变量配置的“提取规则”表格开启 `:sortable="true"`，支持提取规则按需上下拖拽调整触发顺序；4. 在 `ApiEnvParams.vue` 中为“登录接口配置”多选列表集成拖拽排序与悬浮拖拽手柄，并优化 `handleTreeSelectSubmit`，在重新打开选择树增删接口时保留已配置接口的自定义排序；5. 优化 `CommonParamsEdit.vue` 与 `ApiEnvContentWindow.vue` 中拖拽手柄的 CSS 边距，使其与输入控件保持优雅对齐；6. 通过 ESLint 代码规范全量验证。
- **feat**: [2026-07-31] 重构全局变量与提取配置中的登录接口选择与显示交互：1. 解决选中登录接口未展示 Method 等信息的问题，将已选登录接口呈现为简练行卡片，清晰展示 `<ApiMethodTag>`（如 POST/GET 等区别颜色的方法标签）、接口名称及 URL 路径；2. 废弃下拉框内嵌套渲染大型 API 树（`el-tree-select`）的做法，改用 900px 大窗口 `TreeConfigWindow` 树形选择器，彻底解决下拉框搜索卡顿与 DOM 渲染性能瓶颈；3. 提取 `buildLoginApiConfig` 转换函数，消除重构过程中的重复对象构建代码，移除 50 多行非必要自定义 CSS 类，全量使用项目标准 Element Plus 变量与内联 Flex 弹性布局，保证代码极致精简与高可读性；4. 通过 ESLint 代码规范全量验证。
- **opt**: [2026-07-31] 优化导出和生成代码等弹窗中的 API 文档树形节点显示模式：1. 移除 `toShowTreeConfigWindow` 与 `toShowCodeGenConfigWindow` 导出/生成代码弹窗生成节点树时强制传入 `defaultShowLabel: sharePreference.defaultShowLabel` 的逻辑，防止在主界面侧边栏配置为“显示 URL”时导出/生成代码树节点名称被误判替换为纯 URL 路径；2. 参考登录接口配置树（`ApiEnvParams.vue`）的左右双侧渲染模式，在 `TreeIconLabel.vue` 中补全 `.el-tree-node__label` 的 flex 弹性盒拉伸属性并新增 `url` 属性，在基础组件中统一封装左右双侧分栏显示；3. 升级 `ApiDocExportWindow.vue`、`ApiDocCodeGenWindow.vue` 与 `ApiProjectShares.vue` 的树节点模板，左侧清晰展示请求 Method 标签（如 POST/GET）与接口/文件夹名称（`docName`），右侧以次要颜色（`--el-text-color-secondary`）精准右对齐展示接口 URL 路径，同时保证两者名称与路径完整可见；4. 统一将导出、生成代码及全局变量配置等树弹窗宽度调整为适中的 `900px`，兼顾大屏显示完整度与弹窗紧凑度；5. 移除分享配置弹窗中已冗余废弃的「接口显示为名称/显示为URL」切换按钮（`customToggleButtons` / `useCustomDocLabel`），彻底解决左右重复显示同一 URL 的问题；6. 通过 ESLint 代码规范全量验证。
- **feat**: [2026-07-30] 新增登录接口配置与调试工具栏快捷弹窗功能及精简重构：1. 在【全局变量与提取配置】（`ApiEnvParams.vue`）弹窗中增加【登录接口配置】页签，使用 `el-tree-select` 与 `calcProjectItem` 构建标准的项目文件夹与 API 接口树形选择器，只允许选择接口节点并存至 `groupConfig.loginApiConfig`；使用 `cloneDeep` 传递数据避免 `calcProjectItem` 产生 `parent` 循环引用导致的 `Converting circular structure to JSON` 保存报错；合并弹窗数据加载为单一网络请求，减少冗余开销；2. 修复点开登录接口调试弹窗内容为空的问题，登录接口弹窗采用独立 Top 级 Modal 浮层弹窗（`ApiRequestPreviewWindow`），不覆盖底层已在调试的页面或窗口；主界面接口编辑的数据由 Pinia `shareParamTargets` 实时持久化（`watch(paramTarget)` 实时监听），登录弹窗关闭后底层已有调试数据零丢失；对接 Pinia `shareParamTargets` 缓存恢复机制（`preHandler` / `changeHandler` & `copyParamsDynamicOption`），自动同步还原与实时持久化用户之前在登录页面填写的请求参数（如账号/密码/Headers/URL）；在 `ApiDocRequestPreview.vue` 中补全 `handConfig` 可选防御，修复空指针报错；3. 移除独立弹窗中不需要的页面嵌入/新窗口打开按钮 (`OpenInNewFilled` 链接)；4. 还原非相关文件改动，全量通过 ESLint 规则校验。
- **opt**: [2026-07-30] 优化API错误日志与提示展示（简化版）：通过抛出与捕获 `SimpleRuntimeException` 替代大范围的接口及服务层签名修改。在 `SimpleHttpClientUtils` 抛出 HTTP 异常，在 `UrlDocContentProviderImpl` 与 `ApiProjectServiceImpl` 进行统一捕获转换为含详细原因的 `SimpleResult` 返回，保持底层接口（如 `ApiDocImporter`）的代码简洁与纯洁，减少大量文件的改动并提升代码可读性。
- **opt**: [2026-07-30] 优化 API 文档导入及任务执行过程中的错误原因展示与双重日志记录机制：1. 后端 `SimpleHttpClientUtils.java` 增强 GET 请求异常日志输出 `logger.error("执行GET请求错误: url={}", url, e)` 并抛出异常保留根因；2. `UrlDocContentProviderImpl.java` 在网络异常或非 200 HTTP 响应（如 404/500/401）时记录带 URL 与 HTTP 状态行的系统 ERROR 日志，并返回含详细原因的 `SimpleResult`（如 `"URL数据下载失败: HTTP/1.1 404 Not Found"`）；3. `ApiDocImporter.java` 与 `SwaggerImporterImpl.java` 增加 `parseImport` 方法，在 OpenAPI 解析失败时记录包含 `SwaggerParseResult.getMessages()` 的 ERROR 日志并返回拼接的详细错误提示；4. `ApiProjectServiceImpl.java` 与 `ApiProjectImportController.java` 保留完整错误消息透传至前端；5. 修正 `ProjectAutoImportInvoker.java` 导入更新失败日志误引用变量的 Bug，确保底层详细错误原因完整存入 `ApiLog` 数据库表中，便于在系统“日志管理”页面中精准检索与追踪。
- **feat**: [2026-07-29] 增强文档模式（Markdown 模式）下的信息完整性与复制体验：1. 后端 `MarkdownApiDocViewGeneratorImpl.java` 与 `ApiDocFreemarkerUtils.java` 支持解析接口与项目配置的 `securityRequirements` 及 `securitySchemas` 认证规则，并在 `ApiDocMdView.md.ftl` 模板中新增 `### 认证` / `### Authorization` 章节渲染输出（保持后端片段独立，保障项目大文档导出时的标题层级规范）；修正 `messages_zh_CN.properties` 与 `messages.properties` 中的 `api.label.authorization` 词条为 `认证`；2. 前端 `api_cn.js` 与 `api_en.js` 新增 `api.label.copyMarkdown` 国际化词条；3. 前端在 `main.css` 中提取复用全局通用悬浮控件类 `.floating-action-btn`（包含对齐全屏按钮的阴影、边框、过渡色与 16px 精准图标比例）；在 `ApiDocViewer.vue` 中导入 `$copyText` 并于“文档模式”下右下角悬浮工具栈（`bottom: 140px; right: 40px`）渲染一致风格的「复制 Markdown」按钮，并在点击处理函数 `handleCopyMarkdown` 中前端专属动态拼接 `# 接口名称` H1 大标题，支持一键将完整 Markdown 原文复制至剪贴板，并通过 ESLint 代码规范验证。
- **opt**: [2026-07-29] 优化文档模式（Markdown 模式）下的数据模型排序：在 `MarkdownApiDocViewGeneratorImpl.java` 中新增 `sortSchemasMap` 智能重排逻辑，按【请求主模型 -> 响应主模型 -> 关联嵌套模型 -> 其他通用模型】四层严格优先级重新组织 `schemasMap`，使文档模式下底部的“数据模型”列表及右侧大纲（`md-catalog`）最优先置顶显示当前接口的请求体模型与响应体模型；新增 `MarkdownApiDocViewGeneratorImplTest` 单元测试并通过验证。
- **bug**: [2026-07-29] 修复从项目详情进入导入任务列表与分享列表时项目分组名称显示为原始 `groupCode` ID 串的问题：在 `ApiProjectTasks.vue` 和 `ApiProjectShares.vue` 的 `initLoadOnce` 中，当 `inProject` 为 `true` 时补全 `loadGroupsAndRefreshOptions()` 调用，确保初始化即加载当前用户的项目分组数据，使表格渲染项目列时能正确匹配并显示真实的项目分组名称。
- **opt**: [2026-07-29] 优化数据模型/Schema 编辑树添加属性交互体验：1. 在 `ApiComponentSchemaEditTree.vue` 的 `addPropertyInline` 方法中增加父节点自动展开逻辑（`node.expanded = true`）；2. 解决因为 `el-tree` 开启 `lazy` 懒加载及刷新重新挂载 DOM 导致 `nextTick` 无法即时捕捉 DOM 的问题，新增带重试机制的 `scrollToAndFocusNewRow` 异步轮询定位；3. 自动触发居中平滑滚动（`scrollIntoView({ behavior: 'smooth', block: 'center' })`），确保当对象属性较多时新增属性表单即时平滑居中展示在当前视口内；4. 自动定位并聚焦“属性名称”输入框（`input.focus()`），并叠加 2s 柔和渐变脉冲高亮动画（`@keyframes property-highlight`）。
- **feat**: [2026-07-29] 新增 AI 智能生成数据模型功能：1. 后端在 `AiCacheController` 中新增 `/admin/ai/caches/generate-model` 接口，基于业务需求描述生成 PascalCase 格式的模型名称、业务描述及符合 OpenAPI 3.0 / JSON Schema 规范的标准 Schema 结构；2. 前端在 `AiCacheApi.js` 增加 `generateModel` API 接口，并在数据模型表单（`ApiProjectComponent.vue`）“保存”按钮旁边新增简洁无图标的「`AI生成`」按钮，保持与保存、删除、复制等按钮外观一致；3. 提供需求描述输入弹窗（修复 `placeholder` 传递位置，使占位文本正常显示），支持自由选择 AI 配置，并在 `AiCacheList.vue` 中补全 `generate_model` 类型的表格格式化与筛选项；4. 校验并覆盖全部新增国际化词条，并通过 ESLint 代码规范验证。

- **opt**: [2026-07-29] 彻底清理废弃空函数 `syncCachedParamsToTarget`：从 `ApiDocPreviewService.js` 与 `ApiDocRequestPreview.vue` 中彻底擦除无用的 `syncCachedParamsToTarget` 空函数定义与调用点，保持全量代码零冗余。
- **bug**: [2026-07-29] 修复初始尚未配置环境变量时调试面板右上角 `变量` / `Variables` 链接隐藏导致无法入口配置的问题：调整 `ApiEnvPopover.vue` 渲染逻辑为 `v-if="envSuggestions?.length || preferenceId || projectId"`，确保无论初始是否有已解析变量，`变量` 链接均常驻可见，支持随时点开编辑新增变量。
- **bug**: [2026-07-29] 修复点击编辑变量时因 `preferenceId`/`projectCode` 字符串传给 `ApiProjectApi.getById` 导致的后端 `MethodArgumentTypeMismatchException` 类型转换异常：1. 在 `ApiEnvParams.vue` 的 `toEditGroupEnvParams` 中增加 `isNumeric` 防护，当传入非纯数字 ID（如 UUID 格式的 `projectCode` 或 `shareId`）时安全阻止发送后端接口请求，杜绝 Spring MVC 将字符串转为 Long/Integer 失败引发的 500 报错；2. 在 `ApiEnvPopover.vue` 与 `ApiRequestFormReq.vue` 中明确区分与透传数字 `projectId` 与偏好主键 `preferenceId`，保证弹窗能安全发起后端默认配置加载，同时完全独立控制本地持久化缓存。
- **feat**: [2026-07-29] 优化变量 Popover 与本地变量配置弹窗交互：1. 保持 `ApiEnvPopover.vue` 为轻量只读预览浮层，在 Popover 顶栏增加“编辑变量 (`api.label.editVariables`)”链接，点击触发弹出本地模式的 `ApiEnvParams.vue` 完整配置窗口；2. 在 `ApiEnvParams.vue` 弹窗中补全国际化标签 `api.msg.saveLocalOnlyTip` 与 `api.label.resetDefault`；在本地保存模式且本地存在持久化数据时，弹窗底部 Save/Cancel 按钮旁自动增加 `[Reset Default / 恢复默认]` 操作按钮，点击恢复为后端数据库默认配置；3. 清理调试栏多余的独立按钮，保持接口详情 Header 界面简洁干净。
- **bug**: [2026-07-27] 修复只读（无写权限）项目的数据模型及 JSON Schema 树仍可编辑与拖拽的问题：1. 在 `ApiProjectComponent.vue` 中增加 `readonly` 状态控制，给数据模型顶部表单控件添加 `disabled` 状态，并将 Monaco Editor 的 `readOnly` 选项与项目可写权限动态联动；2. 在 `ApiComponentSchemaEditTree.vue` 中新增 `readonly` 属性，利用模板层面的 `:draggable="!readonly"` 以及 `<template v-if="!readonly">` 统一控制只读状态下的拖拽禁用与编辑按钮隐藏，精简去除了多余的函数内校验，保持代码整洁优雅。
- **bug**: [2026-07-24] 修复非管理员用户加载项目分组下拉框时可能越权获取无权限分组导致的“没有权限访问”报错：1. 重构后端 `ApiGroupController.java` 中的 `loadProjectGroups` 接口，强约束基于当前登录人 `SecurityUtils.getLoginUserName()` 真实身份进行 `isAdmin` 校验与 `checkGroupAccess` 权限过滤，禁止非管理员用户通过 `queryVo.userName` 参数骗取未授权分组；2. 前端 `ApiProjectGroupApi.js` 的 `loadGroupsAndRefreshOptions` 增加 `isAdminUser()` 判断保护，确保仅在真正的管理员身份下才向后端传递目标 `userName` 参数，非管理员始终只获取本人的授权分组。
- **opt**: [2026-07-24] 参照 simple-boot-mock-server07 优化项目分组选项与用户信息高亮：1. 在 `ApiProjectGroupApi.js` 统一封装并导出 `renderProjectGroupLabel` 格式化 helper 函数，对所有带有归属用户的项目分组统一格式化展示为 `分组名 (用户名)`，并通过 Element Plus 原生 `<ElText type="success" tag="b">` 绿色高亮展示 `(用户名)`；2. 在 `loadSelectGroups` 中引入稳定排序规则，优先置顶登录人本人的分组，其余分组按归属用户名归类及 `id` 排序，使下拉选项层次清晰分明；3. 在分享管理（`ApiProjectShares.vue`）和定时任务（`ApiProjectTasks.vue`）列表中将项目分组渲染为可点击的 `ElLink` 链接，直接绑定内联 `onClick={() => changeGroup(groupCode)}` 清理多余防护代码；4. 保持公共基础组件 `control-child.vue` 零修改与高兼容度，并通过前端 ESLint 规则校验。
- **bug**: [2026-07-24] 修复禁用 AI 配置但在未刷新页面调用生成时静默降级为默认配置并成功返回数据的问题：1. 在 `SystemErrorConstants.java` 中新增 `CODE_2012`~`CODE_2018` 统一业务错误码，在 `messages*.properties` 补充国际化文案，彻底清理硬编码 HTTP `202` 状态码；2. 重构 `AiServiceImpl.java` 的 `resolveAiConfig` 校验逻辑，配置禁用时抛出 `CODE_2013` 错误码并通过 i18n 动态返回中/英文提示；3. 前端 `ApiCommonService.js` 彻底移除 `code` 判断逻辑，重构为通用的 `resultData`（成功渲染 Payload）与 `res.message`（未包含 Payload 时直接提示消息）架构，并通过 ESLint 规则校验。
- **bug**: [2026-07-24] 修复修改 AI 配置默认状态时历史记录 `is_default` 被误清零的 Bug：在 `AiConfigController.java` 的 `save` 接口中，增加 `isNull("modify_from")` 限定逻辑，防止更新默认 AI 配置时误将全局历史记录 (`modify_from IS NOT NULL`) 的 `is_default` 覆盖更新为 0；同时在 `AiConfigServiceImpl.getDefaultAiConfig` 中增加 `isNull("modify_from")` 约束。
- **opt**: [2026-07-24] 优化 AI 配置管理列表页面与历史记录版本弹窗：1. 将主列表、历史记录版本列表及编辑表单中的“状态”与“设为默认”字段顺序统一调整为“状态”在前、“设为默认”在后；2. 将主列表与历史记录版本列表的所有表格列宽度由固定 `width` 统一调整为响应式 `minWidth`（如 `minWidth: '120px'`, `minWidth: '150px'`, `minWidth: '100px'` 等），防止大屏显示时表头或文本内容被截断；3. 在历史记录版本列表 (`historyColumns`) 中补全“状态”与“设为默认”只读 Tag 标签列；4. “设为默认”(`isDefault`) 主列表列使用原生 `ElSwitch` 绑定 `v-common-tooltip` 动态悬浮提示。
- **feat**: [2026-07-24] 支持 AI 生成时自由选择 AI 配置与默认选中：1. 后端 `/admin/ai/status` 与 `/shares/ai/status` 接口升级，返回包含 enabled 状态、`defaultConfigId` 及启用的 AI 配置列表 (`configs`) 的 `AiStatusVo`；2. 后端 `executeGenericTask` 和 `generateSampleBySchema` 支持接收 `configId` 并优先调用指定配置；3. 前端在生成示例数据弹窗 (`ApiGenerateSampleWindow`) 和 Schema 编辑器补全描述弹窗 (`ApiComponentSchemaEditTree`) 中新增“AI接口配置”下拉框，当有多个 AI 配置时默认选中默认配置，并支持用户切换选择。
- **feat**: [2026-07-24] AI 智能补全缺失描述功能增强：1. 在“AI 智能补全缺失描述”弹窗中新增“附加提示词”多行输入框，允许粘贴外部文档、字段说明或自定义提示词；后端接收该附加提示词并融合至 prompt，使 AI 能精准参考外部文档对应与补全 Schema 属性描述；2. 统一表单项 Label 对齐逻辑（精简 Label 文本为“附加提示词”/“Additional Prompt”，对齐宽度降至 110px），并重构精简前后端参数处理与弹窗打开逻辑。
- **feat**: [2026-07-23] 升级接口调试变量提取引擎：1. 前端引入 `jsonpath-plus` 依赖，重构 `extractVariables` 提取逻辑，支持标准 JSONPath 语法（如高阶数组谓词筛选 `$.data.list[?(@.status==1)].token` 等）；2. 具备 100% 旧配置向下兼容能力，自动将无 `$` 根节点前缀的旧表达式（如 `data.token`）规范化为 `$.data.token`，并提供 Lodash `get` 降级兜底机制；3. 增强透明 XML 响应解析支持，当 Response Body 为 XML 时利用 `fast-xml-parser` 自动解析为内存 JSON 对象进行 JSONPath 提取。
- **feat**: [2026-07-23] 新增按条件批量删除/清空数据模型功能：1. 在 `ProjectComponentQueryVo` 中新增 `checkOnly` 字段，将核对统计与实际删除整合为统一的 `/admin/info/detail/removeByQuery` 接口，严格校验 `DELETABLE` 权限，既能在预检时返回匹配记录与锁定模型数，又能再正式删除时返回 `deletedCount` 并清理关联历史记录 (`modify_from`)；2. 前端基于 `isDeletable` 权限展示“清空数据模型”按钮，两阶段调用统一的 `removeByQuery` 接口，显式配置 `{ loading: true }`，在预检与正式删除过程中均展示全屏 loading 加载指示器，先获取服务端统计数据，再使用 `$coreConfirm` 弹窗向用户展示明细，确认后发起 `checkOnly: false` 正式删除并清空视图。
- **opt**: [2026-07-23] 优化数据模型列表默认排序逻辑：在 `ApiProjectInfoDetailController` 的 `search` 与 `loadInfoDetails` 接口中增加 `orderByDesc("coalesce(modify_date, create_date)", "id")` 排序，确保数据模型列表默认按最近修改时间（当修改时间为空时容错回退为创建时间/ID）降序排列，提升大量数据模型场景下的使用体验。
- **bug**: [2026-07-23] 修复数据模型 (DTO) 历史记录与编辑问题：1. 在 `ApiDocParseUtils.processProjectInfoDetail` 中统一步骤校验 Schema 内容合并/保留后的相等性，消除 COMPONENT/SECURITY 类型无实质变化时产生的冗余历史记录；2. 重构 `SimpleModelUtils.mergeAuditInfo`，直接在目标对象上设置 `modifier` 与 `modifyDate`，消除修改 `existsModel` 导致的历史记录修改时间戳被污染问题；3. 修复已有/导入数据模型数据库中 `data_version` 为 null 导致乐观锁更新失败、数据未更新且反复插入历史记录的问题（在导入及保存时补全默认版本号，并在更新前对 NULL `data_version` 进行静默修复）；4. 修复前端历史列表弹窗“修改时间”列原先写死 `property: 'createDate'` 导致最新版本显示的修改时间比历史版本旧的问题，简化为使用标准 `formatDate(data.modifyDate || data.createDate)` 表达式。
- **bug**: [2026-07-23] 修复重新导入/同步文档时 x-default-auth 认证默认值被覆盖清除的问题：在后端 `ApiDocParseUtils.processProjectInfoDetail` 中对 security 类型的 schemaContent 新增合并逻辑，通过 `ApiSchemaContentUtils.mergeSecuritySchemaContent` 将已保存的各 security schema 的 x-default-auth 字段回填到新导入的 schemaContent 中；同时在合并后重新做相等性判断，避免仅添加 x-default-auth 无实质变更时产生不必要的历史记录。
- **bug**: [2026-07-23] 彻底修复认证弹窗默认 Tab 选中错误问题：根本原因是两个 schema 同时具有 hasDefaultAuth，旧逻辑总选先出现的 JWT 类型（AccessToken）；修复为优先选 TOKEN 类型（$JWT_TOKEN），确保默认显示简化 Token 表单。同步重构：移除 hasAuthValue 值检查，改用 x-default-auth 存在性作为结构性判断；hasInheritAuth 改为基于 schema hasDefaultAuth 而非 defaultAuthModel 是否存在。
- **feat**: [2026-07-22] 简化认证默认值【清空】逻辑：只需直接从内存 Schema 中删除 x-default-auth 节点，并重新调用 calcAuthModelBySchemas 恢复表单标准默认兜底参数，保持组件行为一致与优雅。
- **bug**: [2026-07-17] 修复变量提取规则由于偏好缓存导致未正确生效的 Bug。
- **feat**: [2026-07-16] 优化全局变量提取的路径匹配规则，支持正则表达式，并增加提取成功提示
- **feat**: [2026-07-16] 优化项目详情页的按钮，为认证、数据模型、变量配置等按钮补充数量显示
- **bug**: [2026-07-16] 修复部分变量提取没有正确更新到实际调用的请求中的问题
- **opt**: [2026-07-16] 优化变量配置显示与必填验证逻辑
- **feat**: [2026-07-16] 新增变量配置，并实现把变量应用到请求和认证当中
- **opt**: [2026-07-16] 优化ai缓存以及配置页面查看功能
- **feat**: [2026-07-15] 实现了请求后置脚本与变量提取功能，支持配置全局提取规则并通过 JSONPath 自动从响应中提取数据注入环境变量，完美集成现有认证体系以满足多接口间参数依赖联动（如 Token 自动刷新和提取）。
- **bug**: [2026-07-13] 修复 AI 配置管理中内置 YML 配置与数据库不一致的问题，支持系统配置静默增量同步，并限制内置系统配置的删除及编辑。
- **bug**: [2026-07-13] 修复 AI 配置管理列表部分情况下修改记录及配置列表数据显示格式或空页面的问题。
- **feat**: [2026-07-13] 实现了独立的 AI 接口配置管理模块，支持在后台动态添加、编辑和切换 AI 配置，支持多版本历史管理及回滚，并废弃了从应用配置文件中硬编码的读取方式。
- **opt**: [2026-07-06] 优化 AI 智能补全缺失描述功能，在发起请求前校验是否所有属性均已包含描述，减少资源浪费
- **bug**: [2026-07-06] 修复当 AI 未开启时，生成模型描述按钮仍显示的问题
- **bug**: [2026-07-06] 修复 AI 生成描述功能中对于含有 `items` 的数组及匿名嵌套结构无限误判“属性描述缺失”导致无限弹窗请求的问题；重构并精简了校验代码，提高可维护性
- **bug**: [2026-07-06] 修复开启左右分栏模式时项目列表页面卡片宽度被挤压的问题
- **feat**: [2026-07-06] aiCache管理列表页面增加任务类型(cacheType)显示及筛选项
- **bug**: [2026-07-06] 修复重新导入项目(非锁定状态)会导致在页面上修改的数据模型(DTO)属性descriptions丢失的问题
- **feat**: [2026-07-06] 在数据模型编辑界面新增 AI 智能补全缺失属性描述功能，支持一键智能推断和全量覆盖更新，极大提升旧文档完善效率
### 2026-06
- **feat**: [2026-06-30] 将 AI 缓存管理菜单开放给所有用户，并根据系统 AI 功能启用状态动态显示该菜单
- **bug**: [2026-06-30] 修复AI生成测试数据没有正确记录用户信息的问题
- **opt**: [2026-06-30] 重构项目包结构，将 AiService 移至 ai 包下，合并前端 AiApi.js 和 AiCacheApi.js
- **feat**: [2026-06-30] API调试窗口的CURL功能升级，新增支持复制为cURL(bash)和cURL(cmd)的下拉菜单选项
- **bug**: [2026-06-30] 修复分享页面与后端页面在同浏览器下互相干扰的问题，确保AI生成功能仅在后台展示且配置项相互隔离
- **feat**: [2026-06-30] 完善 AI 缓存信息收集设计，新增提示词(Prompt)、操作用户、项目ID、文档ID、Token消耗以及大模型原始响应和完成时间等维度的记录，方便后续问题排查与额度审计
- **feat**: [2026-06-29] 新增 AI 缓存管理页面，支持管理员查看和管理 AI 调用生成的样本数据缓存
- **feat**: [2026-06-29] 优化 AI 生成数据功能，在分享页隐藏 AI 生成选项，保留后台完整功能，便于内部测试与数据模拟
- **bug**: [2026-07-23] 彻底修复"认证"弹窗默认 Tab 选中错误问题：根本原因是 `calcSecuritySchemas` 将原始 JWT schema 的 `x-default-auth` 同时复制给虚拟 `$JWT_TOKEN` schema，导致两者都有 `hasDefaultAuth=true`，旧逻辑总选先出现的 JWT 类型（AccessToken），显示复杂 JWT 表单；修复为有 `hasDefaultAuth` 时优先选 TOKEN 类型（`$JWT_TOKEN`）而非 JWT 类型，确保默认显示简化的 Token 表单和正确值。同步重构认证相关判断逻辑：移除 `hasAuthValue` 值检查，改用 `x-default-auth` 存在性（`hasDefaultAuth`）作为结构性判断；`hasInheritAuth` 改为基于 schema 的 `hasDefaultAuth` 而非 `defaultAuthModel` 是否存在；每次打开认证弹窗都刷新 model；清空操作后若有 schema 默认值则自动重新初始化。
- **feat**: [2026-06-29] 增加针对 AI 生成请求的数量限制，当排队中的请求过多时会拒绝新的请求，防止耗尽系统线程
- **feat**: [2026-06-26] 支持将 AI 生成或手动编写的数据保存为接口示例，新增管理及删改功能，并保护导入的 OpenAPI 不覆盖原有示例数据
- **feat**: [2026-06-26] AI模拟数据生成功能升级为异步任务池处理，增强前端队列状态响应提示，并支持过期及阻塞状态的数据清理
- **feat**: [2026-06-25] AI生成样本增加数据库持久化缓存功能，提高重复生成效率并节约API调用成本
- **feat**: [2026-06-24] 支持多种生成示例数据的引擎：在点击“生成数据”时弹出对话框，允许用户选择使用 `openapi-sampler`（基础数据）、`Mock.js`（中文随机数据）或 `json-schema-faker`（英文高级Mock数据）来生成 Payload，满足不同场景的调试需求
- **opt**: [2026-06-24] 优化 API 文档展示页面（右侧视图）的间距，减少各标题（如接口描述、认证、请求体）和组件之间的默认边距和内边距，使排版更加紧凑
- **bug**: [2026-06-23] 修复生成示例数据时因递归引用或异常嵌套导致页面卡顿并生成过大Payload（30MB）的问题
- **bug**: [2026-06-15] 修复数据模型编辑弹窗中，切换到 JSON Schema 标签页时 Monaco 编辑器主题颜色未正确应用的问题
- **opt**: [2026-06-15] DTO编辑新增属性改成默认行内编辑，并优化编辑数据模型弹窗中boolean开关选项布局不换行
- **bug**: [2026-06-15] 修复保存新建接口时若不存在项目信息调用 findOrCreateProjectInfo/getOrCreateMountFolder 因事务只读属性报错问题
- **opt**: [2026-06-12] 优化用户编辑/保存数据没有变化就不再重复保存
- **bug**: [2026-06-12] 修复项目页面误显示分组权限编辑按钮问题
- **feat**: [2026-06-12] 项目列表页面新增显示分组用户权限信息
- **feat**: [2026-06-12] 项目列表页面支持新增或修改项目分组功能
- **opt**: [2026-06-12] 项目列表页面支持点击分组名称快速过滤并搜索项目，且有权限时支持在卡片上直接编辑分组
- **opt**: [2026-06-12] 优化用户密码修改功能
- **opt**: [2026-06-12] 优化保存的分组、项目、分享等数据没有变化就不再重复保存
- **bug**: [2026-06-11] 修复部分情况下修改时间显示不正确问题
- **opt**: [2026-06-11] 优化保存的数据没有变化就不再重复保存
- **bug**: [2026-06-11] 修复认证弹框偶尔出现选中tab丢失问题
- **opt**: [2026-06-11] 优化分组权限配置页面，更方便配置
- **opt**: [2026-06-11] 优化导出生成代码安全性
- **version**: [2026-06-11] 更新版本号
- **bug**: [2026-06-11] 修复在初始导入项目的时候误加载文件夹问题
- **feat**: [2026-06-11] 用户关联日志增加支持有权限的项目查询过滤
- **opt**: [2026-06-11] 优化一些操作权限控制防止越权
- **opt**: [2026-06-11] 优化一些doc加载错误的信息展示
- **opt**: [2026-06-11] 增加调试传递url验证，提升安全性
- **opt**: [2026-06-11] 优化share文档的判断权限，防止加载无权限数据
- **feat**: [2026-06-11] 日志管理功能开放给普通用户，可以查看自己的日志
- **opt**: [2026-06-10] 优化用户管理相关权限控制
- **feat**: [2026-06-09] 增加日志清理任务，清理历史日志
- **opt**: [2026-06-08] 优化导入文档性能
- **docs**: [2026-06-08] 新增开源文档网站页面
- **feat**: [2026-06-08] 重新设计新的api文档项目的logo

### 2026-02
- **opt**: [2026-02-08] 优化加载文档详情的loading
- **opt**: [2026-02-08] 分享文档密码验证成功后从页面清除
- **feat**: [2026-02-08] 用户新增编辑新增随机生成密码功能
- **feat**: [2026-02-08] 项目运行环境编辑增加拖动排序功能
- **bug**: [2026-02-08] 修复分享页切换主题时闪烁问题
- **bug**: [2026-02-08] 修复当前页面做接口预览时不能调整宽度问题
- **bug**: [2026-02-02] 修复收起或展开左侧菜单时动画丢失问题
- **bug**: [2026-02-02] 修复左侧菜单收起来时拖动条依然存在的问题
- **opt**: [2026-02-02] 优化项目编辑页面弹窗层样式
- **bug**: [2026-02-02] 修复split的elementSizes计算丢失问题
- **feat**: [2026-02-02] 左侧菜单模式增加split拖动大小调整功能
- **bug**: [2026-02-02] 修复增加登出菜单引起的导航丢失问题
- **bug**: [2026-02-01] 修复主题切换时部分情况动画异常

### 2026-01
- **bug**: [2026-01-31] 修复部分情况下tab显示异常问题
- **opt**: [2026-01-30] 优化分享文档的密码填写页面样式
- **bug**: [2026-01-30] 修复文档加载失败是跳转登录的提示异常问题
- **feat**: [2026-01-30] 分享页面如果有密码，新增一个登出菜单
- **bug**: [2026-01-30] 修复加载md文档无权限时没有跳转登录页问题
- **opt**: [2026-01-30] 优化分享页面也支持主题切换动画
- **opt**: [2026-01-30] 优化多标签模式以及面包屑样式
- **feat**: [2026-01-30] 登录页面重新用AI设计优化
- **feat**: [2026-01-30] 对整个页面的整体风格样式调整
- **feat**: [2026-01-29] add GitHub Actions workflow for building, releasing, and publishing Docker images.
- **bug**: [2026-01-28] 修复API文档编辑页面和展示页面样式不一致问题
- **feat**: [2026-01-28] API项目管理新增在小屏幕下左侧弹出菜单
- **bug**: [2026-01-28] 修复分享页左侧悬浮按钮挡住部分右边部分问本问题
- **opt**: [2026-01-28] 优化文档树节点移动结束时弹框确认取消时刷新机制
- **feat**: [2026-01-28] 左侧API树菜单移动时增加确认操作，防止误操作
- **opt**: [2026-01-28] 优化回到顶部以及全屏按钮显示位置与样式
- **feat**: [2026-01-27] 后台页面右下角增加一个全屏按钮方便展示更多元素
- **bug**: [2026-01-27] 修复导出文档树大小写引起的筛选不正确问题
- **bug**: [2026-01-17] 修复分享页面部分monaco编辑器出现主题不一致的情况
- **opt**: [2026-01-17] 优化请求参数等输入框tabindex控制逻辑
- **opt**: [2026-01-17] 优化文档分享认证失败时的跳转逻辑
- **bug**: [2026-01-17] 修复认证配置不能清空以及再次打开可能出现不一致的情况
- **bug**: [2026-01-16] 修复代理访问时记住登录状态引起的访问错误问题
- **bug**: [2026-01-16] 修复monaco编辑器弹出层在分享页面切换主题无效问题
- **opt**: [2026-01-16] monaco编辑器和主题联动调整
- **feat**: [2026-01-16] 对比工具增加语言选项以及格式化功能
- **opt**: [2026-01-16] 优化json表格工具，支持日期格式化
- **feat**: [2026-01-15] 菜单中对比工具新增记住上次输入功能
- **feat**: [2026-01-15] 代码工具新增按照表格查看快捷图标，显示效果统一
- **bug**: [2026-01-14] 修复一些依赖变更引起的错误

---
*注：本日志基于完整的 Git 提交历史进行深度挖掘与分类汇总。*
