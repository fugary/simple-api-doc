export const MOCK_REQUEST_ID_HEADER = 'mock-request-id'

export const MOCK_DATA_ID_HEADER = 'mock-data-id'

export const MOCK_DATA_MATCH_PATTERN_HEADER = 'mock-data-match-pattern'

export const MOCK_DATA_PATH_PARAMS_HEADER = 'mock-data-path-params'

export const AUTHORIZATION_KEY = 'Authorization'

export const BEARER_KEY = 'Bearer'

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
  value: 'swagger',
  labelKey: 'api.label.importTypeSwagger'
}]

export const IMPORT_TYPES = [{
  value: 'url',
  labelKey: 'api.label.importTypeUrl'
}, {
  value: 'file',
  labelKey: 'api.label.importTypeFile'
}]

export const IMPORT_AUTH_TYPES = AUTH_OPTIONS.filter(type => type.value !== AUTH_TYPE.JWT)

export const ALL_STATUS_CODES = [200, 201, 202, 301, 302, 307, 400, 401, 404, 405, 415, 500, 502, 503]

export const ALL_CONTENT_TYPES = ['application/json', 'application/xml', 'text/html', 'text/css', 'application/javascript', 'application/x-www-form-urlencoded']
