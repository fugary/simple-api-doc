import dayjs from 'dayjs'

export const SIMPLE_API_META_DATA_REQ = 'simple-api-meta-req'

export const AUTHORIZATION_KEY = 'Authorization'

export const BEARER_KEY = 'Bearer'

export const SIMPLE_API_TARGET_URL_HEADER = 'simple-api-target-url'

export const SIMPLE_API_ACCESS_TOKEN_HEADER = 'simple-api-access-token'

export const DEFAULT_PREFERENCE_ID_KEY = 'simple-api-preference-id'

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

export const AUTH_TYPE = {
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

export const IMPORT_AUTH_TYPES = AUTH_OPTIONS.filter(type => type.value !== AUTH_TYPE.JWT)

export const ALL_STATUS_CODES = [200, 201, 202, 301, 302, 307, 400, 401, 404, 405, 415, 500, 502, 503]

export const ALL_CONTENT_TYPES = ['application/json', 'application/xml', 'text/html', 'text/css', 'application/javascript', 'application/x-www-form-urlencoded']
