import { $coreHideLoading, $coreShowLoading } from '@/utils'
import { cloneDeep, isArray, isString } from 'lodash-es'
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
  if (!response.status) {
    response = response.response || {}
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
    data,
    requestInfo,
    requestHeaders,
    responseHeaders
  }
}

export const calcParamTarget = (projectInfoDetail, apiDocDetail) => {
  // const value = previewData?.mockParams || requestItem?.mockParams
  // const requestPath = `/mock/${groupItem.groupPath}${requestItem?.requestPath}`
  const target = {
    pathParams: calcParamTargetByUrl(apiDocDetail.url),
    requestParams: calcSchemaParameters(apiDocDetail.parametersSchema),
    headerParams: calcSchemaParameters(apiDocDetail.parametersSchema, item => item.in === 'header'),
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
  const componentMap = calcComponentMap(projectInfoDetail.componentSchemas)
  console.log('======================target', target, projectInfoDetail, apiDocDetail, componentMap)
  if (apiDocDetail.requestsSchemas?.length) {
    const requestSchemas = apiDocDetail.requestsSchemas.flatMap(apiSchema => processSchemas(apiSchema, componentMap, true))
    console.log('======================requestSchemas', requestSchemas)
    target.requestBodySchema = requestSchemas[0]?.schema
  }
  return target
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
 * @param filter
 * @returns {*[]}
 */
export const calcSchemaParameters = (parametersSchema, filter = item => item.in === 'query') => {
  if (parametersSchema?.schemaContent) {
    const paramSchemas = JSON.parse(parametersSchema.schemaContent)
    if (isArray(paramSchemas)) {
      return paramSchemas.filter(filter).map(param => {
        return {
          name: param.name,
          enabled: true,
          valueRequired: param.required,
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
    if (schema?.[xxxOf]?.length) {
      return xxxOf
    }
  }
}

export const checkLeaf = schema => {
  if (schema?.$ref || Object.keys(schema?.properties || {}).length || hasXxxOf(schema)) {
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

export const processSchemaChildren = schema => {
  const xxxOf = hasXxxOf(schema)
  if (xxxOf) {
    return schema[xxxOf].map(value => {
      return {
        name: value.name,
        schema: value,
        isLeaf: checkLeaf(value)
      }
    })
  }
  return processProperties(schema)
}

/**
 * 特殊处理schema的属性为数组
 * @param schema
 * @return {{schema: *, name: *}[]}
 */
export const processProperties = schema => {
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
    if (recursive && properties) {
      Object.keys(properties).forEach(key => {
        const property = properties[key]
        processSchema(property, componentsMap, recursive)
      })
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

export const calcSecuritySchemas = (projectInfoDetail, securitySchemas, supportedAuthTypes) => {
  if (projectInfoDetail?.securitySchemas?.length) {
    const secSchemas = JSON.parse(projectInfoDetail.securitySchemas[0].schemaContent)
    Object.keys(secSchemas).forEach((key) => {
      const secSchema = secSchemas[key]
      secSchema.authType = calcAuthType(secSchema)
      secSchema.isSupported = Object.values(AUTH_TYPE).includes(secSchema.authType)
    })
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
      authContentModel.tokenPrefix = authSchema.scheme || ''
    }
  }
}

export const calcAuthModelBySchemas = (authContentModel, securitySchemas) => {
  // 找到匹配的模式，优先考虑 authType 相同的模式
  if (securitySchemas) {
    const foundSchema = Object.values(securitySchemas).find(authSchema =>
      authContentModel.authType !== AUTH_TYPE.NONE && authContentModel.authType === authSchema.authType
    ) || Object.values(securitySchemas).find(authSchema => authSchema.isSupported)
    // 使用找到的模式或处理默认值
    calcDefaultAuthModel(authContentModel, foundSchema)
  }
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
  const toDebugApi = (...args) => {
    const showDebugWindow = editable ? isMediumScreen.value : isSmallScreen.value
    if (showDebugWindow) {
      hideDebugSplit()
      previewApiRequest(...args)
    } else {
      if (!isShowDebug.value) {
        splitSizes.value = isLargeScreen.value ? [20, 40, 40] : [0, 50, 50]
        previewLoading.value = true
        nextTick(() => { // 延迟才能获取到Ref实例
          apiDocPreviewRef.value?.toPreviewRequest(...args)
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
