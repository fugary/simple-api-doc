import { $coreHideLoading, $coreShowLoading } from '@/utils'
import { cloneDeep, isArray, isString } from 'lodash-es'
import { hasLoading } from '@/vendors/axios'
import {
  FORM_DATA,
  FORM_URL_ENCODED,
  LANG_TO_CONTENT_TYPES,
  SIMPLE_API_META_DATA_REQ,
  NONE,
  REQUEST_SEND_MODES
} from '@/consts/ApiConstants'
import axios from 'axios'
import { processEvnParams } from '@/services/api/ApiCommonService'

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
  // if (value) {
  //   const pathParams = target.pathParams
  //   const savedTarget = JSON.parse(value)
  //   delete savedTarget.method
  //   delete savedTarget.responseBody
  //   if (target.method === 'GET') {
  //     delete savedTarget.requestBody
  //   }
  //   Object.assign(target, savedTarget || {})
  //   if (savedTarget.pathParams && savedTarget.pathParams.length) {
  //     const savePathParams = savedTarget.pathParams.reduce((result, item) => {
  //       result[item.name] = item.value
  //       return result
  //     }, {})
  //     pathParams.forEach(item => {
  //       item.value = savePathParams[item.name] || ''
  //     })
  //   }
  //   target.pathParams = pathParams
  // }
  // if (groupItem.groupConfig) {
  //   target.groupConfig = JSON.parse(groupItem.groupConfig)
  // }
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

export const checkLeaf = schema => {
  return !['array'].includes(schema?.type) && !schema?.$ref && !Object.keys(schema?.properties || {}).length
}

export const $ref2Schema = $ref => {
  // #/components/schemas/xxx截取
  return $ref.substring($ref.lastIndexOf('/') + 1)
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
    if (value?.type === 'array' && value?.items) {
      if (value?.items?.$ref) {
        value.items.name = $ref2Schema(value.items.$ref)
        value.name = value.items.name
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
    const isArraySchema = schema?.type === 'array'
    if (isArraySchema) {
      parent = schema
      schema = schema.items
    }
    parent.isLeaf = checkLeaf(schema)
    if (schema?.$ref) {
      processSchemaRef(schema, componentsMap)
    }
    processSchemaAllOf(schema)
    parent.isLeaf = checkLeaf(schema)
    const properties = schema.properties
    if (recursive && properties) {
      Object.keys(properties).forEach(key => {
        const property = properties[key]
        processSchema(property, componentsMap, recursive)
      })
    }
    // processSchemaAllOf(apiSchema.schema)
    // apiSchema.isLeaf = checkLeaf(apiSchema.schema)
    // if (isArraySchema && schema.items) {
    //   if (schema.items?.$ref) {
    //     processSchemaRef(schema.items, componentsMap)
    //   }
    //   processSchemaAllOf(schema.items)
    //   properties = schema.items.properties
    // } else {
    //   if (schema?.$ref) {
    //     processSchemaRef(schema, componentsMap)
    //   }
    //   processSchemaAllOf(schema)
    //   parent.isLeaf = checkLeaf(schema)
    //   properties = schema.properties
    // }
    // if (recursive && properties) {
    //   properties.forEach(property => {
    //     processSchema(property, componentsMap, recursive)
    //   })
    // }
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

export const processSchemaAllOf = schema => {
  if (schema?.allOf?.length && !schema.properties) {
    let properties = {}
    schema.allOf.forEach(child => {
      properties = { ...properties, ...child.properties }
    })
    schema.properties = properties
  }
  return schema
}
