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
api.label.authorization = '认证'
api.label.default = '默认'
api.label.statusCode = '状态码'
api.label.matchPattern = '匹配规则'
api.label.interfaces = '接口'
api.label.mockEnv = 'Mock变量'
api.label.project = '项目'
api.label.defaultProject = '默认项目'
api.label.groupName = '分组名称'
api.label.pathId = '路径ID'
api.label.proxyUrl = '代理地址'
api.label.export = '导出'
api.label.exportAll = '导出全部'
api.label.exportSelected = '导出选中部分'
api.label.import = '导入'
api.label.linkAddress = '链接地址'
api.label.importFile = '导入文件'
api.label.source = '数据来源'
api.label.duplicateStrategy = '重复路径处理'
api.label.selectFile = '选择文件'
api.label.importDuplicateStrategyAbort = '中止导入'
api.label.importDuplicateStrategySkip = '跳过重复路径'
api.label.importDuplicateStrategyGenerate = '自动生成新路径'
api.label.importTypeSwagger = 'Swagger2/OpenAPI3.0'
api.label.sendRequest = '发送请求'
api.label.authType = '认证方式'
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

api.msg.noExportData = '没有需要导出的数据'
api.msg.exportConfirm = '确认导出Mock数据？'
api.msg.proxyUrlTooltip = '配置的请求之外的地址将发送到代理地址获取数据，支持http和https'
api.msg.proxyUrlMsg = '代理地址必须是正常的http或者https地址'
api.msg.pathIdMsg = '建议不要填写，自动生成'
api.msg.duplicateStrategy = '路径是全局唯一的，所有用户共享，因此通常为自动生成的uuid'
api.msg.importFileTitle = '导入mock数据'
api.msg.importFileLimit = '文件大小最大限制为5MB'
api.msg.importFileSuccess = '导入成功，共{0}条'
api.msg.importFileNoFile = '请选择导入文件'
api.msg.showRawData = '显示未格式化原始数据'
api.msg.saveMockResponse = '保存响应数据，【发送请求】测试将自动保存'
api.msg.pasteToProcess = '支持浏览器GET字符串或者JSON'
api.msg.authParamNameTooltip = '认证默认值为：{0}，不能为空'
api.msg.authPrefixTooltip = '前缀默认值为：{0}，可以为空'
api.msg.requestTest = '请求测试'
api.msg.matchPatternTest = '匹配规则测试'
api.msg.requestNameTooltip = '简单接口名称，可不填写'
api.msg.requestIntro = `request.body——body内容对象<br>
                        request.bodyStr——body内容字符串<br>
                        request.headers——头信息对象<br>
                        request.parameters——请求参数对象<br>
                        request.pathParameters——路径参数对象<br>
                        request.params——请求参数和路径参数合并`
api.msg.matchPatternTooltip = `匹配规则支持javascript表达式，支持的request请求数据: <br>${api.msg.requestIntro}`
api.msg.projectCodeTooltip = '字母、数字、_-组成，唯一标识'
api.msg.responseBodyTooltip = `响应内容支持请求参数替换，使用{0}格式替换数据，支持的request请求数据: <br>${api.msg.requestIntro}`
