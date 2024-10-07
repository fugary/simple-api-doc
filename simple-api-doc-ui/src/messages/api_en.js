import { baseMessages } from '@/messages/base'
export const api = baseMessages()

api.label.method = 'Method'
api.label.requestBody = 'Request Body'
api.label.responseBody = 'Response Body'
api.label.mockResponseBody = 'Mock Response'
api.label.requestBody1 = 'Request'
api.label.responseBody1 = 'Response'
api.label.requestPath = 'Request Path'
api.label.requestName = 'Request Name'
api.label.shareName = 'Share Name'
api.label.importName = 'Import Name'
api.label.taskName = 'Task Name'
api.label.expireDate = 'Expire Date'
api.label.noExpires = 'No Expires'
api.label.exportEnabled = 'Export Enabled'
api.label.debugEnabled = 'Debug Enabled'
api.label.debugAPI = 'Debug API'
api.label.environments = 'Environments'
api.label.accessPassword = 'Access Password'
api.label.accessDocs = 'Access Docs'
api.label.hasPassword = 'Has Password'
api.label.openLink = 'Open Link'
api.label.copyLink = 'Copy Link'
api.label.copyLinkAndPassword = 'Copy Link and Password'
api.label.pathParams = 'Path Params'
api.label.queryParams = 'Query Params'
api.label.requestHeaders = 'Request Headers'
api.label.responseHeaders = 'Response Headers'
api.label.authorization = 'Authorization'
api.label.default = 'Default'
api.label.statusCode = 'Status Code'
api.label.matchPattern = 'Match Pattern'
api.label.interfaces = 'Interfaces'
api.label.apiDescription = 'API Description'
api.label.mdDocument = 'Document'
api.label.docName = 'Doc Name'
api.label.folder = 'Folder'
api.label.subFolder = 'Sub Folder'
api.label.mockEnv = 'Mock Env'
api.label.project = 'Project'
api.label.apiProjects = 'API Projects'
api.label.defaultProject = 'Default Project'
api.label.folderName = 'Folder Name'
api.label.targetFolder = 'Target Folder'
api.label.pathId = 'Path ID'
api.label.proxyUrl = 'Proxy URL'
api.label.browserSend = 'Browser Send'
api.label.serverSend = 'Server Send'
api.label.sendType = 'Send Type'
api.label.corsMode = 'CORS Mode'
api.label.export = 'Export'
api.label.exportAll = 'Export All'
api.label.exportSelected = 'Export Selected'
api.label.import = 'Import'
api.label.linkAddress = 'Link Address'
api.label.source = 'Source'
api.label.importFile = 'Import file'
api.label.importUrl = 'Import URL'
api.label.importData = 'Import Data'
api.label.importNow = 'Import Now'
api.label.execDate = 'Last import time'
api.label.manualImportData = 'Manual Import Data'
api.label.autoImportData = 'Auto Import Data'
api.label.manualImportData1 = 'Manual Trigger'
api.label.autoImportData1 = 'Auto Trigger'
api.label.triggerRate = 'Trigger Rate'
api.label.shareDocs = 'Share Docs'
api.label.duplicateStrategy = 'Duplicate API'
api.label.selectFile = 'Select file'
api.label.importDuplicateStrategyOverride = 'Override Same API'
api.label.importDuplicateStrategyAbort = 'Abort Import'
api.label.importDuplicateStrategySkip = 'Skip Same API'
api.label.importTypeSwagger = 'Swagger2/OpenAPI3.0'
api.label.importType = 'From Type'
api.label.importTypeFile = 'From File'
api.label.importTypeUrl = 'From URL'
api.label.docLabelShowName = 'Show API Name'
api.label.docLabelShowUrl = 'Show API URL'
api.label.sendRequest = 'Send Request'
api.label.authType = 'Auth Type'
api.label.authTypeNone = 'No Auth'
api.label.authTypeBasic = 'Basic Auth'
api.label.authTypeToken = 'Token Auth'
api.label.authTypeJWT = 'JWT Auth'
api.label.authParamName = 'Param Name'
api.label.authPrefix = 'Prefix'
api.label.setDefault = 'Set default'
api.label.dataFormat = 'Data Format'
api.label.projectCode = 'Project Code'
api.label.projectName = 'Project Name'

api.msg.noExportData = 'No data to export'
api.msg.exportConfirm = 'Confirm to export mock data?'
api.msg.proxyUrlTooltip = 'Requests outside the configured address will be sent to the proxy address to fetch data. Supports http and https'
api.msg.proxyUrlMsg = 'Proxy address must be a valid http or https address'
api.msg.pathIdMsg = 'It is recommended not to fill in, it will be generated automatically'
api.msg.duplicateStrategy = 'The path is globally unique, and all users share it, so it is usually automatically generated by uuid'
api.msg.importFileTitle = 'Import data'
api.msg.importFileLimit = 'File size limit is 5MB'
api.msg.importFileSuccess = 'Imported successfully [{0}].'
api.msg.importFileNoFile = 'Please select import file'
api.msg.showRawData = 'Show raw data without formatting'
api.msg.saveMockResponse = 'Save response data, [Send Request] test will automatically save'
api.msg.pasteToProcess = 'Supports url query string or JSON'
api.msg.authParamNameTooltip = 'Default value is: {0}, cannot be empty'
api.msg.authPrefixTooltip = 'Default value is: {0}, can be empty'
api.msg.requestTest = 'Test Request'
api.msg.sendTypeTooltip = 'Browser send requires the API support cross-domain requests, otherwise only server-side sending is allowed'
