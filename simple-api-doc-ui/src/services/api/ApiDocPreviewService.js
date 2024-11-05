import { $coreHideLoading, $coreShowLoading } from '@/utils'
import { cloneDeep, isArray, isString, lowerCase } from 'lodash-es'
import { hasLoading } from '@/vendors/axios'
import {
  FORM_DATA,
  FORM_URL_ENCODED,
  LANG_TO_CONTENT_TYPES,
  SIMPLE_API_META_DATA_REQ,
  NONE,
  AUTH_TYPE,
  BEARER_KEY,
  REQUEST_SEND_MODES
} from '@/consts/ApiConstants'
import axios from 'axios'
import { processEvnParams, useScreenCheck } from '@/services/api/ApiCommonService'
import { nextTick, ref, watch, computed } from 'vue'
import { previewApiRequest } from '@/utils/DynamicUtils'
import { calcDetailPreferenceId } from '@/services/api/ApiFolderService'
import { useShareConfigStore } from '@/stores/ShareConfigStore'

export const previewRequest = function (reqData, config) {
  const req = axios.create({
    baseURL: import.meta.env.VITE_APP_API_BASE_URL, // url = base url + request url
    timeout: 60000 // request timeout,
  })
  const headers = config.headers || {}
  config.__startTime = new Date().getTime()
  if (hasLoading(config)) {
    $coreShowLoading(isString(config.loading) ? config.loading : undefined)
  }
  return req(Object.assign({
    url: reqData.url,
    method: reqData.method,
    transformResponse: res => res// 信息不要转换掉，这边需要预览原始信息
  }, config, { headers }))
}

export const processResponse = function (response) {
  console.log('=========================response', response)
  const { config } = response
  if (hasLoading(config)) {
    $coreHideLoading()
  }
  let error = null
  if (response.response) {
    error = response
    response = response.response
  }
  const { headers = {}, request = {}, status } = response
  const requestInfo = {
    url: request.responseURL || config.url,
    method: config.method?.toUpperCase(),
    status,
    duration: config.__startTime ? new Date().getTime() - config.__startTime : 0
  }
  const requestHeaders = JSON.parse(headers[SIMPLE_API_META_DATA_REQ] || '[]').sort((a, b) => a.name.localeCompare(b.name))
  const responseHeaders = []
  for (const name in headers) {
    if (name !== SIMPLE_API_META_DATA_REQ) {
      responseHeaders.push({
        name,
        value: headers[name]
      })
    }
  }
  const data = response.data
  return {
    error,
    data,
    requestInfo,
    requestHeaders,
    responseHeaders
  }
}

export const calcParamTarget = (projectInfoDetail, apiDocDetail) => {
  // const value = previewData?.mockParams || requestItem?.mockParams
  // const requestPath = `/mock/${groupItem.groupPath}${requestItem?.requestPath}`
  const componentMap = calcComponentMap(projectInfoDetail.componentSchemas)
  const target = {
    pathParams: calcSchemaParameters(apiDocDetail.parametersSchema, componentMap, item => item.in === 'path'),
    requestParams: calcSchemaParameters(apiDocDetail.parametersSchema, componentMap),
    headerParams: calcSchemaParameters(apiDocDetail.parametersSchema, componentMap, item => item.in === 'header'),
    [FORM_DATA]: [],
    [FORM_URL_ENCODED]: [],
    method: apiDocDetail?.method || 'GET',
    contentType: apiDocDetail?.contentType || 'application/json',
    requestContentType: apiDocDetail?.contentType || 'application/json',
    requestFormat: 'json',
    targetUrl: apiDocDetail?.targetUrl,
    requestPath: apiDocDetail.url,
    sendType: REQUEST_SEND_MODES[0].value
  }
  if (apiDocDetail.requestsSchemas?.length) {
    const requestSchemas = apiDocDetail.requestsSchemas.flatMap(apiSchema => processSchemas(apiSchema, componentMap, true))
      .map(reqSchema => reqSchema.schema).filter(schema => !!schema)
    target.requestBodySchema = requestSchemas
  }
  target.securityRequirements = calcSecurityRequirements(projectInfoDetail, apiDocDetail)
  console.log('======================target', target, apiDocDetail, componentMap)
  return target
}

/**
 * 解析SecurityRequirements，优先Operation级别数据
 * @param projectInfoDetail
 * @param apiDocDetail
 * @returns {*}
 */
export const calcSecurityRequirements = (projectInfoDetail, apiDocDetail) => {
  const securityRequirements = apiDocDetail.securityRequirements || projectInfoDetail?.securityRequirements?.[0]
  if (securityRequirements?.schemaContent) {
    const secRequirements = JSON.parse(securityRequirements?.schemaContent)
    return secRequirements.flatMap(sec => Object.keys(sec).map(key => lowerCase(key)))
  }
}

export const preProcessParams = (params = []) => {
  return params.filter(param => param.enabled && !!param.name)
}

/**
 * 各种类型的body解析
 *
 * @param paramTarget
 * @return {{data: (string|*), hasBody: boolean}}
 */
export const calcRequestBody = (paramTarget) => {
  const contentType = paramTarget.value.requestContentType
  let data = paramTarget.value.requestBody
  let hasBody = true
  if (contentType === NONE) {
    data = undefined
    hasBody = false
  } else if (contentType === LANG_TO_CONTENT_TYPES[FORM_DATA]) {
    data = new FormData()
    preProcessParams(paramTarget.value[FORM_DATA]).forEach(item => {
      if (isArray(item.value)) {
        item.value.filter(file => !!file?.raw).forEach(file => data.append(item.name, file.raw))
      } else {
        data.append(item.name, item.value)
      }
    })
  } else if (contentType === LANG_TO_CONTENT_TYPES[FORM_URL_ENCODED]) {
    const params = preProcessParams(paramTarget.value[FORM_URL_ENCODED])
    data = Object.fromEntries(params.map(item => [item.name, processEvnParams(paramTarget.value.groupConfig, item.value)]))
  }
  if (isString(data)) {
    data = processEvnParams(paramTarget.value.groupConfig, data)
  }
  return {
    hasBody,
    data
  }
}

export const calcParamTargetByUrl = (calcRequestUrl) => {
  return calcRequestUrl.split('/').filter(seg => seg.match(/^[:{]/))
    .map(seg => seg.replace(/[:{}]/g, ''))
    .reduce((newArr, arrItem) => {
      if (newArr.indexOf(arrItem) < 0) {
        newArr.push(arrItem)
      }
      return newArr
    }, []).map(name => {
      return {
        name,
        value: ''
      }
    })
}

/**
 * 请求参数Schema计算
 * @param parametersSchema
 * @param componentMap
 * @param filter
 * @returns {*[]}
 */
export const calcSchemaParameters = (parametersSchema, componentMap, filter = item => item.in === 'query') => {
  if (parametersSchema?.schemaContent) {
    const paramSchemas = JSON.parse(parametersSchema.schemaContent)
    if (isArray(paramSchemas)) {
      return paramSchemas.filter(filter).map(param => {
        param.schema && processSchema(param, componentMap, true)
        return {
          name: param.name,
          value: param.schema?.default || '',
          enabled: true,
          valueRequired: param.required,
          valueSuggestions: param.schema?.enum,
          dynamicOption: () => ({ required: param.required })
        }
      })
    }
  }
  return []
}

export const checkParamsFilled = (params) => {
  return !params?.length || params.filter(param => param.enabled).every(param => param.name && param.value)
}

//* **************************schema处理***************************

export const calcComponentMap = (componentSchemas) => {
  return componentSchemas.reduce((res, apiSchema) => {
    if (!apiSchema.schema && apiSchema.schemaContent) {
      apiSchema.schema = JSON.parse(apiSchema.schemaContent) // 从文本解析出来
    }
    res[apiSchema.schemaKey] = apiSchema
    return res
  }, {})
}
/**
 * 判断是否有xxxOf
 *
 * @param schema
 * @returns {string}
 */
export const hasXxxOf = schema => {
  for (const xxxOf of ['allOf', 'anyOf', 'oneOf']) {
    if (schema?.[xxxOf]?.length || schema?.items?.[xxxOf]?.length) {
      return xxxOf
    }
  }
}

/**
 * 判断是否有additionalProperties
 *
 * @param schema
 * @returns {boolean}
 */
export const hasAddProperties = schema => {
  return !!schema?.additionalProperties
}

export const checkLeaf = schema => {
  if (schema?.$ref || Object.keys(schema?.properties || {}).length || hasXxxOf(schema) || hasAddProperties(schema)) {
    return false
  }
  if (isArraySchema(schema)) {
    return checkLeaf(schema.items)
  }
  return true
}

export const isArraySchema = schema => {
  return (schema?.type === 'array' || schema?.types?.includes('array')) && !!schema.items
}

export const $ref2Schema = $ref => {
  // #/components/schemas/xxx截取
  return $ref.substring($ref.lastIndexOf('/') + 1)
}

export const processSchemaChildren = (schema, mergeAllOf = false) => {
  const xxxOf = hasXxxOf(schema)
  const children = []
  if (xxxOf) {
    const xxxOfList = schema[xxxOf] || schema.items?.[xxxOf] || []
    if (!mergeAllOf || xxxOf !== 'allOf') {
      children.push(...xxxOfList.map(value => {
        return {
          name: value.name,
          schema: value,
          isLeaf: checkLeaf(value)
        }
      }))
    } else {
      children.push(...xxxOfList.flatMap(xxxSchema => processSchemaChildren(xxxSchema, mergeAllOf)))
    }
  }
  children.push(...processProperties(schema))
  if (hasAddProperties(schema)) {
    children.push({
      schema: schema.additionalProperties,
      isLeaf: checkLeaf(schema.additionalProperties),
      isAdditional: true
    })
  }
  return children
}

/**
 * 特殊处理schema的属性为数组
 * @param schema
 * @return {{schema: *, name: *}[]}
 */
export const processProperties = schema => {
  if (!schema) {
    return []
  }
  const properties = schema.items?.properties || schema.properties || {}
  const schemaParent = schema.items || schema
  return Object.keys(properties).map(key => {
    const value = properties[key]
    if (value.$ref) {
      value.name = $ref2Schema(value.$ref)
    }
    if (isArraySchema(value)) {
      value.name = value.items.name
      if (value.items?.$ref) {
        value.items.name = $ref2Schema(value.items.$ref)
      } else if (value.items.type !== 'object') {
        value.name = value.items.type
      }
    }
    if (schemaParent.required?.length) {
      value.isRequired = schemaParent.required.includes(key)
    }
    return {
      name: key,
      schema: value,
      isLeaf: checkLeaf(value)
    }
  })
}

export const processSchemas = (docSchema, componentsMap, recursive = false) => {
  const saveSchema = JSON.parse(docSchema.schemaContent)
  let resultArr = []
  if (isArray(saveSchema)) {
    resultArr = saveSchema.map(schema => processSchema(schema, componentsMap, recursive))
  } else {
    resultArr = [processSchema(saveSchema, componentsMap, recursive)]
  }
  resultArr.filter(schema => !!schema?.schema)
    .forEach(schema => (schema.schema.__contentType = docSchema.contentType))
  return resultArr
}

export const processSchema = (apiSchema, componentsMap, recursive = false) => {
  if (apiSchema) {
    let parent = {}
    let schema = apiSchema
    if (apiSchema.schema) {
      parent = apiSchema
      schema = apiSchema.schema
    }
    if (isArraySchema(schema)) {
      parent = schema
      schema = schema.items
    }
    parent.isLeaf = checkLeaf(schema)
    if (schema?.$ref) {
      processSchemaRef(schema, componentsMap)
    }
    // processSchemaAllOf(schema)
    processSchemaXxxOf(schema, componentsMap, recursive)
    parent.isLeaf = checkLeaf(schema)
    const properties = schema.properties
    if (properties) {
      Object.keys(properties).forEach(key => {
        const property = properties[key]
        if (recursive) {
          processSchema(property, componentsMap, recursive)
        } else {
          processSchemaRef(property, componentsMap) // 仅处理下$ref，方便展示
        }
      })
    }
    if (hasAddProperties(schema)) {
      processSchema(schema.additionalProperties, componentsMap, recursive)
    }
  }
  return apiSchema
}

export const processSchemaRef = (schema, componentsMap) => {
  if (schema.$ref) {
    const apiSchema = cloneDeep(componentsMap[schema.$ref]?.schema)
    if (!apiSchema) {
      console.log('==============================$ref-null', schema.$ref, apiSchema)
    }
    apiSchema.name = $ref2Schema(schema.$ref)
    Object.assign(schema, apiSchema)
    delete schema.$ref
  }
  return schema
}

export const processSchemaXxxOf = (schema, componentsMap, recursive) => {
  ['allOf', 'anyOf', 'oneOf'].forEach(xxxOf => {
    if (schema?.[xxxOf]?.length) {
      schema[xxxOf].forEach(child => {
        processSchema(child, componentsMap, recursive)
      })
    }
  })
}

/**
 * 删除deprecated数据
 *
 * @param schema
 * @returns {*}
 */
export const removeSchemaDeprecated = schema => {
  const properties = schema?.properties
  if (properties) {
    Object.keys(properties).forEach(key => {
      if (properties[key]?.deprecated) {
        delete properties[key]
      } else {
        properties[key] = removeSchemaDeprecated(properties[key])
      }
    })
  }
  return schema
}

export const calcSecuritySchemas = (projectInfoDetail, securitySchemas, supportedAuthTypes) => {
  if (projectInfoDetail?.securitySchemas?.length) {
    const secSchemas = JSON.parse(projectInfoDetail.securitySchemas[0].schemaContent)
    let jwtKey = null
    Object.keys(secSchemas).forEach((key) => {
      const secSchema = secSchemas[key]
      secSchema.authType = calcAuthType(secSchema)
      secSchema.isSupported = Object.values(AUTH_TYPE).includes(secSchema.authType)
      secSchema.authType === AUTH_TYPE.JWT && (jwtKey = key)
      secSchema.authKey = key
    })
    const authTypes = Object.values(secSchemas).map(secSchema => secSchema.authType)
    if (authTypes.includes(AUTH_TYPE.JWT) && !authTypes.includes(AUTH_TYPE.TOKEN)) { // jwtToken添加一个直接的token模式，方便使用
      const jwtSchema = secSchemas[jwtKey]
      if (jwtSchema) {
        secSchemas.$JWT_TOKEN = {
          description: 'JWT authentication with token only.',
          authType: AUTH_TYPE.TOKEN,
          name: jwtSchema.name,
          in: jwtSchema.in,
          scheme: jwtSchema.scheme,
          type: jwtSchema.type,
          isSupported: true,
          authKey: jwtKey
        }
      }
    }
    console.log('==================================secSchemas', secSchemas)
    securitySchemas.value = secSchemas
    supportedAuthTypes.value = Object.values(secSchemas).filter(s => s.isSupported)
      .map(s => s.authType)
    return secSchemas
  }
  return undefined
}

export const calcAuthType = (schema) => {
  const typeStr = schema?.type?.toLowerCase()
  if (!typeStr) {
    return AUTH_TYPE.NONE
  }
  const schemeStr = schema?.scheme?.toLowerCase()
  if (schemeStr === AUTH_TYPE.BASIC) {
    return AUTH_TYPE.BASIC
  }
  if (schemeStr === BEARER_KEY.toLowerCase() || typeStr === 'apikey') {
    if (schema.bearerFormat?.toLowerCase() === AUTH_TYPE.JWT) {
      return AUTH_TYPE.JWT
    }
    return AUTH_TYPE.TOKEN
  }
  return schema?.type
}

export const calcDefaultAuthModel = (authContentModel, authSchema) => {
  if (authContentModel && authSchema) {
    authContentModel.authType = authSchema.authType || AUTH_TYPE.NONE
    if (authSchema.name) {
      authContentModel.headerName = authSchema.name
    }
    authContentModel.tokenToType = authSchema.in === 'query' ? 'parameter' : 'header'
    if (authSchema.in === 'query') {
      authContentModel.tokenToType = 'parameter'
    }
    if (authContentModel.authType === AUTH_TYPE.TOKEN) {
      authContentModel.tokenPrefix = authSchema.scheme || (BEARER_KEY === authSchema.authKey ? BEARER_KEY : '')
    }
    authContentModel.authKey = authSchema.authKey
    return authContentModel
  }
}

export const calcAuthModelBySchemas = (apiDocDetail, authContentModel, securitySchemas) => {
  // 找到匹配的模式，优先考虑 authType 相同的模式
  const requirements = calcSecurityRequirements(apiDocDetail?.projectInfoDetail, apiDocDetail) || []
  if (securitySchemas) {
    const authModelsMap = Object.fromEntries((authContentModel.authModels || []).map(authModel => [authModel.authType, authModel]))
    const secSchemas = Object.values(securitySchemas).filter(authSchema => authSchema.isSupported)
    authContentModel.authModels = secSchemas.map(secSchema => {
      const authModel = calcDefaultAuthModel({}, secSchema)
      // 从已保存数据复制
      Object.assign(authModel, authModelsMap[authModel.authType] || {})
      authModel.isSupported = requirements.includes(lowerCase(authModel.authKey))
      secSchema.authModel = authModel
      return authModel
    })
    if (authContentModel.authType === AUTH_TYPE.NONE) {
      authContentModel.authType = authContentModel.authModels?.find(authModel => authModel.isSupported)?.authType || AUTH_TYPE.NONE
    }
    console.log('================================authModels', authContentModel)
  }
}

const defaultCheckFunc = schema => !schema?.__contentType || schema?.__contentType?.includes('json') || schema?.__contentType?.includes('*/*')

export const generateSampleCheck = (schemaBody, checkFun = defaultCheckFunc) => {
  let parsedSchemaBody = isString(schemaBody) ? JSON.parse(schemaBody) : schemaBody
  if (parsedSchemaBody) {
    if (!isArray(parsedSchemaBody)) {
      parsedSchemaBody = [parsedSchemaBody]
    }
    return parsedSchemaBody.find(schema => checkFun(schema))
  }
}

export const generateSampleCheckResults = schemaBody => {
  const results = []
  const jsonSchema = generateSampleCheck(schemaBody)
  jsonSchema && results.push({
    type: 'json',
    schema: jsonSchema
  })
  const xmlSchema = generateSampleCheck(schemaBody, schema => {
    return !!schema?.xml || !!schema?.__contentType?.includes('xml')
  })
  xmlSchema && results.push({
    type: 'xml',
    schema: xmlSchema
  })
  const formUrlEncoded = generateSampleCheck(schemaBody, schema => {
    return !!schema?.__contentType?.includes('x-www-form-urlencoded')
  })
  formUrlEncoded && results.push({
    type: FORM_URL_ENCODED,
    schema: formUrlEncoded
  })
  const formData = generateSampleCheck(schemaBody, schema => {
    return !!schema?.__contentType?.includes('form-data')
  })
  formData && results.push({
    type: FORM_DATA,
    schema: formData
  })
  return results
}

export const useApiDocDebugConfig = (editable = false) => {
  const apiDocPreviewRef = ref()
  const splitSizes = ref([20, 80])
  const previewLoading = ref(false)
  const defaultMinSizes = ref([0, 500, 600])
  const defaultMaxSizes = ref([500, Infinity, Infinity])
  const isShowDebug = computed(() => splitSizes.value?.length === 3)
  const { isSmallScreen, isMediumScreen, isLargeScreen } = useScreenCheck()
  const hideDebugSplit = () => {
    if (isShowDebug.value) {
      splitSizes.value = [20, 80]
    }
  }
  const toDebugApi = (projectInfo, apiDoc, ...args) => {
    let showDebugWindow = editable ? isMediumScreen.value : isSmallScreen.value
    const preferenceId = calcDetailPreferenceId(apiDoc)
    const shareConfigStore = useShareConfigStore()
    if (shareConfigStore?.sharePreferenceView?.[preferenceId]?.debugInWindow) {
      showDebugWindow = true
    }
    if (showDebugWindow) {
      hideDebugSplit()
      previewApiRequest(projectInfo, apiDoc, ...args)
    } else {
      if (!isShowDebug.value) {
        splitSizes.value = isLargeScreen.value ? [20, 40, 40] : [0, 50, 50]
        previewLoading.value = true
        nextTick(() => { // 延迟才能获取到Ref实例
          apiDocPreviewRef.value?.toPreviewRequest(projectInfo, apiDoc, ...args)
            .finally(() => { previewLoading.value = false })
        })
      } else {
        hideDebugSplit()
      }
    }
  }
  watch(isSmallScreen, () => {
    if (isSmallScreen.value) {
      hideDebugSplit()
    }
  })
  return {
    apiDocPreviewRef,
    splitSizes,
    defaultMinSizes,
    defaultMaxSizes,
    isShowDebug,
    previewLoading,
    hideDebugSplit,
    toDebugApi
  }
}
