import dayjs from 'dayjs'

export const SIMPLE_API_META_DATA_REQ = 'simple-api-meta-req'

export const AUTHORIZATION_KEY = 'Authorization'

export const BEARER_KEY = 'Bearer'

export const SIMPLE_API_TARGET_URL_HEADER = 'simple-api-target-url'

export const SIMPLE_API_ACCESS_TOKEN_HEADER = 'simple-api-access-token'

export const CURRENT_SHARE_THEME_KEY = 'simple-api-share-theme'

export const DEFAULT_PREFERENCE_ID_KEY = 'simple-api-preference-id'

export const SCHEMA_COMPONENT_PREFIX = '#/components/schemas/'

export const DEFAULT_HEADERS = ['Accept',
  'Accept-Charset',
  'Accept-Encoding',
  'Accept-Language',
  'Authorization',
  'Cookie',
  'Connection',
  'Content-Type',
  'Origin',
  'Pragma',
  'User-Agent'
]
export const NONE = 'none'
export const FORM_DATA = 'formData'
export const FORM_URL_ENCODED = 'formUrlencoded'
export const SPECIAL_LANGS = [NONE, FORM_DATA, FORM_URL_ENCODED]

export const LANG_TO_CONTENT_TYPES = {
  json: 'application/json',
  javascript: 'application/json',
  html: 'application/xml',
  xmlWithJs: 'application/xml',
  text: 'text/plain',
  [FORM_DATA]: 'multipart/form-data',
  [FORM_URL_ENCODED]: 'application/x-www-form-urlencoded'
}

export const calcContentType = (lang, body) => {
  if (lang === 'html' && body?.includes('<!DOCTYPE html>')) {
    return 'text/html'
  }
  return LANG_TO_CONTENT_TYPES[lang]
}

export const calcContentLanguage = contentType => {
  if (contentType) {
    const charIndex = contentType?.indexOf(';')
    if (charIndex > -1) {
      contentType = contentType.substring(0, charIndex)
    }
    return CONTENT_TYPES_TO_LANG[contentType]
  }
}

export const AUTH_TYPE = {
  INHERIT: 'inherit',
  NONE,
  BASIC: 'basic',
  TOKEN: 'token',
  JWT: 'jwt'
}

export const AUTH_PARAM_NAMES = [AUTHORIZATION_KEY, 'accessToken', 'access_token', 'token', 'jwt_token', 'api_key', 'X-API-Key']
export const AUTH_PREFIX_NAMES = [BEARER_KEY, 'Basic']

export const ALL_METHODS = [
  { method: 'GET', type: 'primary' },
  { method: 'POST', type: 'success' },
  { method: 'DELETE', type: 'danger' },
  { method: 'PUT', type: 'warning' },
  { method: 'PATCH', type: 'info' },
  { method: 'HEAD', type: 'info' },
  { method: 'OPTIONS', type: 'info' },
  { method: 'TRACE', type: 'info' }]

export const AUTH_OPTIONS = [{
  value: AUTH_TYPE.INHERIT,
  labelKey: 'api.label.authTypeInherit',
  enabled: false
}, {
  value: AUTH_TYPE.NONE,
  labelKey: 'api.label.authTypeNone'
}, {
  value: AUTH_TYPE.BASIC,
  labelKey: 'api.label.authTypeBasic'
}, {
  value: AUTH_TYPE.TOKEN,
  labelKey: 'api.label.authTypeToken'
}, {
  value: AUTH_TYPE.JWT,
  labelKey: 'api.label.authTypeJWT'
}]

export const IMPORT_DUPLICATE_STRATEGY = [{
  value: 3,
  labelKey: 'api.label.importDuplicateStrategyOverride'
}, {
  value: 1,
  labelKey: 'api.label.importDuplicateStrategyAbort'
}, {
  value: 2,
  labelKey: 'api.label.importDuplicateStrategySkip'
}]

export const IMPORT_SOURCE_TYPES = [{
  value: 'openapi',
  labelKey: 'api.label.importTypeSwagger'
}]

export const IMPORT_TYPES = [{
  value: 'file',
  labelKey: 'api.label.importTypeFile'
}, {
  value: 'url',
  labelKey: 'api.label.importTypeUrl'
}]

export const IMPORT_TASK_TYPES = [{
  value: 'manual',
  labelKey: 'api.label.manualImportData1'
}, {
  value: 'auto',
  labelKey: 'api.label.autoImportData1'
}]

export const REQUEST_SEND_MODES = [{
  value: 'server',
  labelKey: 'api.label.serverSend'
}, {
  value: 'browser',
  labelKey: 'api.label.browserSend'
}]

export const TASK_TRIGGER_RATES = [5, 10, 30, 60, 60 * 3, 60 * 12, 60 * 24, 60 * 24 * 3, 60 * 24 * 7].map(minutes => {
  return {
    value: minutes * 60,
    label: dayjs.duration(minutes, 'minutes').humanize()
  }
})

export const TASK_STATUS_MAPPING = {
  started: 'primary',
  done: 'success',
  running: 'success',
  error: 'danger',
  stopped: 'danger'
}

export const AUTHORITY_TYPE = {
  FORBIDDEN: 'forbidden',
  READABLE: 'readable',
  WRITABLE: 'writable',
  DELETABLE: 'deletable'
}

export const AUTHORITY_TYPE_MAPPING = {
  readable: 'primary',
  writable: 'success',
  deletable: 'warning',
  forbidden: 'danger'
}

export const ALL_AUTHORITIES = Object.values(AUTHORITY_TYPE).map(value => {
  return {
    value,
    labelKey: `api.label.authority_${value}`
  }
})

export const IMPORT_AUTH_TYPES = AUTH_OPTIONS.filter(type => type.value !== AUTH_TYPE.JWT)

export const ALL_STATUS_CODES = [
  { code: 200, labelCn: '成功', labelEn: 'OK' },
  { code: 201, labelCn: '已创建', labelEn: 'Created' },
  { code: 202, labelCn: '已接受', labelEn: 'Accepted' },
  { code: 203, labelCn: '非权威信息', labelEn: 'Non-Authoritative Information' },
  { code: 204, labelCn: '无内容', labelEn: 'No Content' },
  { code: 205, labelCn: '重置内容', labelEn: 'Reset Content' },
  { code: 206, labelCn: '部分内容', labelEn: 'Partial Content' },
  { code: 301, labelCn: '永久重定向', labelEn: 'Moved Permanently' },
  { code: 302, labelCn: '临时重定向', labelEn: 'Temporary Redirect' },
  { code: 304, labelCn: '未修改', labelEn: 'Not Modified' },
  { code: 307, labelCn: '临时重定向', labelEn: 'Temporary Redirect' },
  { code: 308, labelCn: '永久重定向', labelEn: 'Permanent Redirect' },
  { code: 400, labelCn: '错误请求', labelEn: 'Bad Request' },
  { code: 401, labelCn: '未授权', labelEn: 'Unauthorized' },
  { code: 403, labelCn: '禁止访问', labelEn: 'Forbidden' },
  { code: 404, labelCn: '未找到', labelEn: 'Not Found' },
  { code: 405, labelCn: '方法不允许', labelEn: 'Method Not Allowed' },
  { code: 406, labelCn: '不可接受', labelEn: 'Not Acceptable' },
  { code: 409, labelCn: '冲突', labelEn: 'Conflict' },
  { code: 410, labelCn: '已删除', labelEn: 'Gone' },
  { code: 411, labelCn: '需要有效长度', labelEn: 'Length Required' },
  { code: 412, labelCn: '前置条件失败', labelEn: 'Precondition Failed' },
  { code: 413, labelCn: '请求实体过大', labelEn: 'Payload Too Large' },
  { code: 414, labelCn: '请求URI过长', labelEn: 'URI Too Long' },
  { code: 415, labelCn: '不支持的媒体类型', labelEn: 'Unsupported Media Type' },
  { code: 416, labelCn: '请求范围不满足', labelEn: 'Range Not Satisfiable' },
  { code: 417, labelCn: '期望失败', labelEn: 'Expectation Failed' },
  { code: 418, labelCn: '我是茶壶', labelEn: "I'm a teapot" },
  { code: 422, labelCn: '无法处理的实体', labelEn: 'Unprocessable Entity' },
  { code: 429, labelCn: '请求过多', labelEn: 'Too Many Requests' },
  { code: 431, labelCn: '请求头字段过大', labelEn: 'Request Header Fields Too Large' },
  { code: 451, labelCn: '因法律原因不可用', labelEn: 'Unavailable For Legal Reasons' },
  { code: 500, labelCn: '服务器内部错误', labelEn: 'Internal Server Error' },
  { code: 501, labelCn: '未实现', labelEn: 'Not Implemented' },
  { code: 502, labelCn: '错误网关', labelEn: 'Bad Gateway' },
  { code: 503, labelCn: '服务不可用', labelEn: 'Service Unavailable' },
  { code: 504, labelCn: '网关超时', labelEn: 'Gateway Timeout' }
]

export const ALL_CONTENT_TYPES = ['*/*', 'application/json', 'application/xml', 'text/html', 'text/css', 'application/javascript', 'application/x-www-form-urlencoded']

export const SCHEMA_BASIC_TYPE_CONFIGS = [
  {
    value: 'string',
    supportedPropConfigs: [
      { name: 'required', type: 'switch' },
      { name: 'nullable', type: 'switch' },
      { name: 'deprecated', type: 'switch' },
      { name: 'enum', type: 'input-tag' },
      { name: 'const', type: 'input' },
      { name: 'default', labelKey: 'api.label.defaultValue', type: 'input' },
      {
        name: 'format',
        type: 'select',
        options: [
          'date',
          'date-time',
          'password',
          'byte',
          'binary',
          'email',
          'uuid',
          'uri',
          'hostname',
          'ipv4',
          'ipv6'
        ],
        startMore: true
      },
      { name: 'examples', type: 'input-tag' },
      { name: 'pattern', type: 'input' },
      { name: 'minLength', type: 'input-number' },
      { name: 'maxLength', type: 'input-number' }
    ]
  },
  {
    value: 'integer',
    supportedPropConfigs: [
      { name: 'required', type: 'switch' },
      { name: 'nullable', type: 'switch' },
      { name: 'deprecated', type: 'switch' },
      { name: 'enum', type: 'input-tag' },
      { name: 'const', type: 'input-number' },
      { name: 'default', labelKey: 'api.label.defaultValue', type: 'input-number' },
      {
        name: 'format',
        type: 'select',
        options: [
          'int32',
          'int64'
        ],
        startMore: true
      },
      { name: 'examples', type: 'input-tag' },
      { name: 'minimum', type: 'input-number' },
      { name: 'maximum', type: 'input-number' },
      { name: 'exclusiveMinimum', type: 'switch' },
      { name: 'exclusiveMaximum', type: 'switch' },
      { name: 'multipleOf', type: 'input-number' }
    ]
  },
  {
    value: 'number',
    supportedPropConfigs: [
      { name: 'required', type: 'switch' },
      { name: 'nullable', type: 'switch' },
      { name: 'deprecated', type: 'switch' },
      { name: 'enum', type: 'input-tag' },
      { name: 'const', type: 'input-number' },
      { name: 'default', labelKey: 'api.label.defaultValue', type: 'input-number' },
      {
        name: 'format',
        type: 'select',
        options: [
          'float',
          'double'
        ],
        startMore: true
      },
      { name: 'examples', type: 'input-tag' },
      { name: 'minimum', type: 'input-number' },
      { name: 'maximum', type: 'input-number' },
      { name: 'exclusiveMinimum', type: 'switch' },
      { name: 'exclusiveMaximum', type: 'switch' },
      { name: 'multipleOf', type: 'input-number' }
    ]
  },
  {
    value: 'boolean',
    supportedPropConfigs: [
      { name: 'required', type: 'switch' },
      { name: 'nullable', type: 'switch' },
      { name: 'deprecated', type: 'switch' },
      { name: 'enum', type: 'input-tag' }, // true/false
      {
        name: 'const',
        type: 'select',
        options: [true, false]
      },
      {
        name: 'default',
        labelKey: 'api.label.defaultValue',
        type: 'select',
        options: [true, false]
      },
      { name: 'examples', type: 'input-tag' }
    ]
  },
  {
    value: 'object',
    supportedPropConfigs: [
      { name: 'nullable', type: 'switch' },
      { name: 'deprecated', type: 'switch' },
      { name: 'required', type: 'input-tag', labelKey: 'api.label.requiredProperties' }, // object: required 是属性名数组
      { name: 'properties', type: 'common-form-label' },
      { name: 'additionalProperties', type: 'switch' },
      { name: 'minProperties', type: 'input-number', startMore: true },
      { name: 'maxProperties', type: 'input-number' },
      { name: 'examples', type: 'input-tag' }
    ]
  },
  {
    value: 'array',
    supportedPropConfigs: [
      { name: 'required', type: 'switch' },
      { name: 'nullable', type: 'switch' },
      { name: 'deprecated', type: 'switch' },
      { name: 'minItems', type: 'input-number', startMore: true },
      { name: 'maxItems', type: 'input-number' },
      { name: 'uniqueItems', type: 'switch' },
      { name: 'examples', type: 'input-tag' }
    ]
  },
  {
    value: 'any',
    supportedPropConfigs: [
      { name: 'required', type: 'switch' },
      { name: 'nullable', type: 'switch' },
      { name: 'deprecated', type: 'switch' },
      { name: 'default', labelKey: 'api.label.defaultValue', type: 'input' },
      { name: 'examples', type: 'input-tag' }
    ]
  }
]

export const SCHEMA_BASE_TYPES = SCHEMA_BASIC_TYPE_CONFIGS.map(type => type.value)

export const SCHEMA_XXX_OF_TYPES = ['allOf', 'oneOf', 'anyOf']

export const SCHEMA_SELECT_TYPE = {
  BASIC: 'basic',
  REF: 'ref',
  XXX_OF: 'xxxOf'
}

export const SCHEMA_SELECT_TYPES = [{
  value: SCHEMA_SELECT_TYPE.BASIC,
  labelKey: 'api.label.typeBasic'
}, {
  value: SCHEMA_SELECT_TYPE.REF,
  labelKey: 'api.label.typeRef'
}, {
  value: SCHEMA_SELECT_TYPE.XXX_OF,
  labelKey: 'api.label.typeXxxOf'
}]

export const SECURITY_IN_TYPES = ['header', 'query', 'cookie']

export const SECURITY_TYPE_TYPES = ['apiKey', 'http', 'oauth2', 'openIdConnect', 'mutualTLS']

export const SECURITY_OAUTH2_AUTH_TYPES = ['authorizationCode', 'clientCredentials', 'implicit', 'password']

export const ALL_CONTENT_TYPES_LIST = [
  { contentType: 'application/json', text: true },
  { contentType: 'application/xml', text: true },
  { contentType: 'text/html', text: true },
  { contentType: 'text/plain', text: true },
  { contentType: 'text/css', text: true },
  { contentType: 'application/javascript', text: true },
  { contentType: 'text/javascript', text: true },
  { contentType: 'text/event-stream', text: true },
  { contentType: 'application/x-www-form-urlencoded', response: false },
  { contentType: 'multipart/form-data', response: false },
  { contentType: 'image/png' },
  { contentType: 'image/jpeg' },
  { contentType: 'image/gif' },
  { contentType: 'application/pdf' },
  { contentType: 'application/zip' },
  { contentType: 'audio/mpeg' },
  { contentType: 'audio/ogg' },
  { contentType: 'video/mp4' },
  { contentType: 'video/ogg' },
  { contentType: 'application/graphql', text: true },
  { contentType: 'application/yaml', text: true },
  { contentType: 'text/markdown', text: true },
  { contentType: 'application/octet-stream' },
  { contentType: 'application/vnd.ms-excel' },
  { contentType: 'application/msword' },
  { contentType: 'application/vnd.ms-powerpoint' }
]

export const CONTENT_TYPES_TO_LANG = {
  'application/json': 'json',
  'application/xml': 'html',
  'application/javascript': 'javascript',
  'text/javascript': 'javascript',
  'text/html': 'html',
  'text/plain': 'text',
  'text/event-stream': 'text',
  'text/css': 'css'
}

export const CHARSET_LIST = ['UTF-8', 'ISO-8859-1', 'GBK', 'GB2312', 'GB18030', 'UTF-16']

export const LANGUAGE_LIST = [
  'zh',
  'zh-CN',
  'zh-TW',
  'zh-HK',
  'en',
  'en-US',
  'en-GB',
  'en-AU',
  'ja',
  'ja-JP',
  'ko',
  'ko-KR',
  'fr',
  'fr-FR',
  'fr-CA',
  'de',
  'de-DE',
  'es',
  'es-ES',
  'es-MX',
  'it',
  'it-IT',
  'pt',
  'pt-BR',
  'pt-PT',
  'ru',
  'ru-RU',
  'ar',
  'ar-SA',
  'th',
  'th-TH',
  'vi',
  'vi-VN'
]

export const LANGUAGE_LIST1 = LANGUAGE_LIST.flatMap(item =>
  item.includes('-') ? [item, item.replace('-', '_')] : [item]
)
