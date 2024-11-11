import { baseMessages } from '@/messages/base'
export const api = baseMessages()

api.label.method = '请求方法'
api.label.requestBody = '请求体'
api.label.responseBody = '响应体'
api.label.mockResponseBody = 'Mock响应体'
api.label.pathParams = '路径参数'
api.label.queryParams = '请求参数'
api.label.requestHeaders = '请求头'
api.label.responseHeaders = '响应头'
api.label.requestBody1 = '请求'
api.label.responseBody1 = '响应'
api.label.requestPath = '请求路径'
api.label.requestName = '请求名称'
api.label.shareName = '分享名称'
api.label.importName = '导入名称'
api.label.taskName = '任务名称'
api.label.runningStatus = '执行状态'
api.label.scheduleStatus = '调度状态'
api.label.expireDate = '过期时间'
api.label.noExpires = '长期有效'
api.label.exportEnabled = '允许导出'
api.label.debugEnabled = '允许调试'
api.label.showChildrenLength = '子节点数量'
api.label.copyRight = '版权信息'
api.label.debugAPI = '调试接口'
api.label.environments = '运行环境'
api.label.accessPassword = '访问密码'
api.label.accessDocs = '访问文档'
api.label.hasPassword = '有密码'
api.label.openLink = '打开链接'
api.label.copyLink = '复制链接'
api.label.copyLinkAndPassword = '复制链接和密码'
api.label.authorization = '认证'
api.label.default = '默认'
api.label.statusCode = '状态码'
api.label.matchPattern = '匹配规则'
api.label.interfaces = '接口'
api.label.apiDescription = '接口描述'
api.label.mdDocument = '文档'
api.label.docName = '文档名称'
api.label.docContent = '文档内容'
api.label.folder = '文件夹'
api.label.subFolder = '子文件夹'
api.label.mockEnv = 'Mock变量'
api.label.project = '项目'
api.label.apiProjects = 'API项目'
api.label.defaultProject = '默认项目'
api.label.folderName = '文件夹名称'
api.label.targetFolder = '目标文件夹'
api.label.pathId = '路径ID'
api.label.proxyUrl = '代理地址'
api.label.browserSend = '浏览器发送'
api.label.serverSend = '服务端发送'
api.label.sendType = '发送类型'
api.label.export = '导出'
api.label.exportAll = '导出全部'
api.label.exportSelected = '导出选中部分'
api.label.exportSelectedApi = '导出部分接口'
api.label.import = '导入'
api.label.linkAddress = '链接地址'
api.label.defaultAddress = '默认'
api.label.importFile = '导入文件'
api.label.importUrl = '导入URL'
api.label.importData = '导入数据'
api.label.importNow = '立即导入'
api.label.execDate = '最近执行时间'
api.label.manualImportData = '手动导入数据'
api.label.autoImportData = '定时导入数据'
api.label.manualImportData1 = '手动触发'
api.label.autoImportData1 = '定时触发'
api.label.triggerRate = '触发频率'
api.label.stopTask = '停止任务'
api.label.stopAndDisable = '停止并禁用'
api.label.taskManagement = '任务管理'
api.label.shareManagement = '分享管理'
api.label.importManagement = '导入管理'
api.label.shareDocs = '文档分享'
api.label.source = '数据来源'
api.label.duplicateStrategy = '相同API处理'
api.label.selectFile = '选择文件'
api.label.importDuplicateStrategyOverride = '覆盖相同API'
api.label.importDuplicateStrategyAbort = '中止导入'
api.label.importDuplicateStrategySkip = '跳过相同API'
api.label.importTypeSwagger = 'Swagger2/OpenAPI3'
api.label.importType = '导入方式'
api.label.importTypeFile = '文件导入'
api.label.importTypeUrl = 'URL导入'
api.label.docLabelShowName = '接口显示为名称'
api.label.docLabelShowUrl = '接口显示为URL'
api.label.mergeAllOf = '合并展示allOf'
api.label.unMergeAllOf = '不合并展示allOf'
api.label.debugInModalWindow = '以弹窗形式调试'
api.label.debugInFitScreen = '自适应调试窗口'
api.label.clearCachedData = '清理已缓存数据'
api.label.sendRequest = '发送请求'
api.label.authType = '认证方式'
api.label.authTypeInherit = '继承配置'
api.label.authTypeNone = '无认证'
api.label.authTypeBasic = 'Basic认证'
api.label.authTypeToken = 'Token认证'
api.label.authTypeJWT = 'JWT认证'
api.label.authParamName = '认证参数名'
api.label.authPrefix = '前缀'
api.label.setDefault = '设为默认'
api.label.dataFormat = '数据格式'
api.label.projectCode = '项目编码'
api.label.projectName = '项目名称'
api.label.projectIcon = '项目图标'
api.label.enum = '枚举值'
api.label.notSupported = '暂不支持'
api.label.additionalProperties = '额外字段'
api.label.generateClientCode = '生成客户端代码'
api.label.generateCodeConfig = '配置生成参数'
api.label.selectToGenerate = '选择接口生成'
api.label.selectShareDocs = '选择分享文档'
api.label.shareAllDocs = '分享全部文档'
api.label.shareSelectedDocs = '分享选中的{0}个文档'
api.label.generatorProvider = '生成器来源'
api.label.generatorProviderSwagger = 'Swagger Generator'
api.label.historyVersions = '历史版本'
api.label.versionDiff = '版本对比'
api.label.compare = '比较'
api.label.viewDiff = '查看变更'
api.label.current = '当前'

api.msg.noExportData = '没有需要导出的数据'
api.msg.exportConfirm = '确认导出API文档数据？'
api.msg.generateCodeConfirm = '确认生成客户端代码？'
api.msg.proxyUrlTooltip = '配置的请求之外的地址将发送到代理地址获取数据，支持http和https'
api.msg.proxyUrlMsg = '地址必须是正常的http或者https地址'
api.msg.pathIdMsg = '建议不要填写，自动生成'
api.msg.duplicateStrategy = '路径是全局唯一的，所有用户共享，因此通常为自动生成的uuid'
api.msg.importFileTitle = '导入数据'
api.msg.importFileLimit = '文件大小最大限制为5MB'
api.msg.importFileSuccess = '导入成功【{0}】'
api.msg.importFileNoFile = '请选择导入文件'
api.msg.showRawData = '显示未格式化原始数据'
api.msg.saveMockResponse = '保存响应数据，【发送请求】测试将自动保存'
api.msg.pasteToProcess = '支持浏览器GET字符串或者JSON'
api.msg.authParamNameTooltip = '认证默认值为：{0}，不能为空'
api.msg.authPrefixTooltip = '前缀值可以为空'
api.msg.requestTest = '请求测试'
api.msg.sendTypeTooltip = '浏览器发送需要接口支持跨域请求，否则仅能用服务端发送'
api.msg.docNeedPassword = '当前文档需要密码才可访问'
api.msg.gotoProjectDetails = '是否前往项目详情页？'
api.msg.noApiSelected = '没有选中任何API文档'
api.msg.authNotSupported = '当前接口不支持此认证方式'
api.msg.projectIconTooltip = '支持图片格式：png,jpg,jpeg,gif等，长宽比为1:1'
