import {
  $coreConfirm,
  formatDate,
  getSingleSelectOptions,
  includesAnyIgnoreCase,
  isUserAdmin,
  useCurrentUserName
} from '@/utils'
import { $i18nKey } from '@/messages'
import { sample } from 'openapi-sampler'
import { XMLBuilder } from 'fast-xml-parser'
import { cloneDeep, isArray, isFunction, isObject, isString, isPlainObject } from 'lodash-es'
import { ALL_CONTENT_TYPES, ALL_CONTENT_TYPES_LIST, CHARSET_LIST, LANGUAGE_LIST1 } from '@/consts/ApiConstants'
import { useElementSize, useMediaQuery } from '@vueuse/core'
import { processSchemas, removeSchemaDeprecated } from '@/services/api/ApiDocPreviewService'
import { APP_VERSION } from '@/config'
import { ref, h, computed } from 'vue'
import { defineTableButtons } from '@/components/utils'
import { showCodeWindow } from '@/utils/DynamicUtils'

export const generateSchemaSample = (schemaBody, type) => {
  return $coreConfirm($i18nKey('common.msg.commonConfirm', 'common.label.generateData'))
    .then(() => {
      let schema = isString(schemaBody) ? JSON.parse(schemaBody) : cloneDeep(schemaBody)
      schema = removeSchemaDeprecated(schema)
      console.log('===========================schema', schema)
      const json = sample(schema)
      let resStr
      if (type?.includes('xml')) {
        const builder = new XMLBuilder({
          ignoreAttributes: false,
          format: true,
          indentBy: '\t'
        })
        const rootName = schema.xml?.name || 'root'
        const xml = {
          [rootName]: json
        }
        resStr = builder.build(xml)
      } else {
        resStr = JSON.stringify(json)
      }
      return resStr
    })
}
/**
 * 显示示例
 *
 * @param requestsSchema
 * @param componentMap
 * @returns {Promise<void>}
 */
export const showGenerateSchemaSample = async (requestsSchema, componentMap) => {
  let sampleStr = ''
  if (requestsSchema.examples) {
    const examples = JSON.parse(requestsSchema.examples)
    sampleStr = examples?.[0]?.value && JSON.stringify(examples[0].value)
  }
  if (!sampleStr) {
    const generateSchema = processSchemas(requestsSchema, componentMap, true)
    console.log('===================generateSchema', generateSchema)
    if (generateSchema[0]?.schema) {
      sampleStr = await generateSchemaSample(generateSchema[0].schema, requestsSchema.contentType)
    }
  }
  if (sampleStr) {
    return showCodeWindow(sampleStr, { readOnly: false })
  }
}

export const generateFormSample = (schemaBody) => {
  const results = []
  if (schemaBody.schema?.properties) {
    Object.keys(schemaBody.schema.properties).forEach(name => {
      const value = schemaBody.schema.properties[name]
      const valueRequired = schemaBody.schema.required?.includes(name)
      results.push({
        name,
        value: value?.example,
        enabled: true,
        tooltip: value?.description,
        valueSuggestions: schemaBody.schema?.enum,
        dynamicOption: () => ({ required: valueRequired })
      })
    })
  }
  return results
}

export const calcSuggestionsFunc = (keySuggestions) => {
  if (isFunction(keySuggestions)) {
    return keySuggestions
  } else if (isArray(keySuggestions)) {
    return (queryString, cb) => {
      const dataList = keySuggestions.map(value => isObject(value) ? value : ({ value }))
        .filter(item => {
          let valueStr = item?.value ?? ''
          valueStr = isString(valueStr) ? valueStr : valueStr.toString()
          return valueStr.toLowerCase?.().includes(queryString?.toLowerCase())
        })
      cb(dataList)
    }
  }
}
/**
 * 组合多个Suggestions配置
 * @param args
 * @returns {(function(*, *): void)|*}
 */
export const concatValueSuggestions = (...args) => {
  const suggestionsArr = args?.filter(suggestions => !!suggestions)
  if (suggestionsArr?.length) {
    return (queryString, cb) => {
      const dataList = []
      suggestionsArr.forEach(suggestions => {
        const callback = items => isArray(items) && dataList.push(...items)
        const suggestionsFunc = calcSuggestionsFunc(suggestions)
        if (suggestionsFunc) {
          suggestionsFunc(queryString, callback)
        }
      })
      cb(dataList)
    }
  }
}

export const calcEnvSuggestions = (groupConfig) => {
  if (groupConfig) {
    groupConfig = isString(groupConfig) ? JSON.parse(groupConfig) : groupConfig
    return (groupConfig?.envParams || [])
      .filter(param => param.enabled && param.name)
      .map(param => `{{${param.name}}}`)
  }
}

export const processEvnParams = (groupConfig, dataValue, encode) => {
  if (groupConfig && isString(dataValue) && dataValue.includes('{{') && dataValue.includes('}}')) {
    groupConfig = groupConfig && isString(groupConfig) ? JSON.parse(groupConfig) : groupConfig
    if (groupConfig?.envParams?.length) {
      groupConfig?.envParams.filter(param => param.enabled && param.name && isString(param.value)).forEach(item => {
        dataValue = dataValue.replace(`{{${item.name}}}`, (item.value || '').trim())
      })
    }
  }
  if (isString(dataValue) && encode) {
    dataValue = encodeURIComponent(dataValue)
  }
  return dataValue
}

export const useContentTypeOption = (prop = 'contentType') => {
  return {
    label: 'Content Type',
    prop,
    type: 'select',
    children: getSingleSelectOptions(...ALL_CONTENT_TYPES),
    attrs: {
      clearable: false
    }
  }
}

/**
 * 屏幕大小判断
 * @returns {{isMediumScreen: vue_demi.Ref<boolean>, isSmallScreen: vue_demi.Ref<boolean>, isMobile: vue_demi.Ref<boolean>}}
 */
export const useScreenCheck = () => {
  const isMobile = useMediaQuery('(max-width: 768px)')
  const isSmallScreen = useMediaQuery('(max-width: 1200px)')
  const isMediumScreen = useMediaQuery('(max-width: 1400px)')
  const isLargeScreen = useMediaQuery('(min-width: 1400px)')
  return {
    isMobile,
    isSmallScreen,
    isMediumScreen,
    isLargeScreen
  }
}

export const useContainerCheck = (size = 500) => {
  const containerRef = ref()
  const { width } = useElementSize(containerRef)
  const isSmallContainer = computed(() => {
    return containerRef.value && width.value < size
  })
  return {
    width,
    isSmallContainer,
    containerRef
  }
}

/**
 * copyRight计算
 * @param [shareDoc]
 * @return {Ref<UnwrapRef<string>, UnwrapRef<string> | string>}
 */
export const useCopyRight = (shareDoc) => {
  const copyRight = ref(`Copyright © ${formatDate(new Date(), 'YYYY')} Version: ${APP_VERSION}`)
  if (shareDoc?.copyRight) {
    const shareCopyRight = shareDoc.copyRight.replace('{{version}}', APP_VERSION)
    copyRight.value = `Copyright © ${formatDate(new Date(), 'YYYY')} ${shareCopyRight}`
  }
  return h('span', { innerHTML: copyRight.value })
}

export const checkUserAuthAccess = (userName, accessData, authority) => {
  if (isUserAdmin(userName)) {
    return true
  }
  if (!accessData.groupCode) {
    return userName === accessData.userName
  }
  let authorities = accessData.authorities
  if (authorities && isString(authorities)) {
    authorities = authorities.split(',').map(auth => auth.trim())
  }
  if (authorities?.length) {
    return authorities.includes(authority)
  }
  return false
}

export const checkCurrentAuthAccess = (accessData, authority) => {
  return checkUserAuthAccess(useCurrentUserName(), accessData, authority)
}

export const useCustomDocLabel = () => {
  const customDocLabel = ref('docName')
  const customToggleButtons = computed(() => {
    return defineTableButtons([{
      type: 'success',
      labelKey: customDocLabel.value === 'docName' ? 'api.label.docLabelShowUrl' : 'api.label.docLabelShowName',
      click () {
        customDocLabel.value = customDocLabel.value === 'docName' ? 'url' : 'docName'
      }
    }])
  })
  return {
    customDocLabel,
    customToggleButtons
  }
}

export const isJson = str => {
  if (isString(str)) {
    str = str.trim()
    return str.startsWith('{') || str.startsWith('[')
  }
  return false
}

export const isXml = str => {
  if (isString(str)) {
    str = str.trim()
    return str.startsWith('<') && str.endsWith('>') && !str.toLowerCase().includes('<!doctype html>')
  }
  return false
}

/**
 * 查找：
 * 1. 第一个数组的值
 * 2. key 为 x.x.x 形式（作为 lodash.get 路径）
 */
export function findArrayAndPath (obj) {
  let arrayData
  const arrayPath = []

  function traverse (current, path = []) {
    if (!current || typeof current !== 'object') return
    if (Array.isArray(current)) return
    for (const key of Object.keys(current)) {
      const value = current[key]
      const currentPath = [...path, key]
      if (Array.isArray(value) && isPlainObject(value[0])) {
        arrayPath.push(currentPath)
        if (arrayData === undefined) {
          arrayData = value
        }
      } else {
        traverse(value, currentPath)
      }
    }
  }

  traverse(obj)

  return {
    data: obj,
    arrayData,
    arrayPath
  }
}

export function checkArrayAndPath (jsonStr) {
  if (isJson(jsonStr)) {
    try {
      return findArrayAndPath(JSON.parse(jsonStr))
    } catch (e) {
      console.error('解析json错误', e)
    }
  }
  return {}
}

const HEADER_SUGGESTIONS = [{
  keys: ['accept-encoding', 'content-encoding'],
  values: ['gzip', 'deflate', 'br']
}, {
  keys: ['accept', 'content-type'],
  values: ALL_CONTENT_TYPES_LIST.map(i => i.contentType)
}, {
  keys: ['cache-control'],
  values: ['no-cache', 'no-store', 'max-age=3600']
}, {
  keys: ['authorization'],
  values: ['Bearer ', 'Basic ']
}, {
  keyWords: ['charset', 'encoding'],
  values: CHARSET_LIST
}, {
  keyWords: ['language', 'locale', 'lang'],
  values: LANGUAGE_LIST1
}]

export const calcHeaderSuggestions = name => {
  if (!name) return []
  const header = HEADER_SUGGESTIONS.find(h =>
    includesAnyIgnoreCase(name, h.keys) || includesAnyIgnoreCase(name, h.keyWords)
  )
  return header ? header.values : []
}
