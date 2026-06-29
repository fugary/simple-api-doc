import { ElMessage } from 'element-plus'
import {
  formatDate,
  getSingleSelectOptions,
  includesAnyIgnoreCase,
  isUserAdmin,
  $coreSuccess,
  $corePrompt,
  $coreConfirm,
  useCurrentUserName
} from '@/utils'
import { sample } from 'openapi-sampler'
import Mock from 'mockjs'
import { generate } from 'json-schema-faker'
import { fakerZH_CN as faker } from '@faker-js/faker'
import { XMLBuilder } from 'fast-xml-parser'
import { cloneDeep, isArray, isFunction, isObject, isString, isPlainObject } from 'lodash-es'
import { ALL_CONTENT_TYPES, ALL_CONTENT_TYPES_LIST, CHARSET_LIST, LANGUAGE_LIST1 } from '@/consts/ApiConstants'
import { useElementSize, useMediaQuery } from '@vueuse/core'
import { processSchemas } from '@/services/api/ApiDocPreviewService'
import { APP_VERSION } from '@/config'
import { ref, h, computed } from 'vue'
import { defineTableButtons } from '@/components/utils'
import { showCodeWindow, showGenerateSampleWindow } from '@/utils/DynamicUtils'
import { aiGenerateSample } from '@/api/AiApi'
import { $i18nMsg, $i18nBundle, $i18nKey } from '@/messages'

import { updateExamples } from '@/api/ApiProjectInfoDetailApi'

const SCHEMA_ARRAY_KEYS = ['allOf', 'anyOf', 'oneOf', 'prefixItems']
const SCHEMA_OBJECT_KEYS = ['items', 'additionalProperties', 'contains', 'if', 'then', 'else', 'not', 'propertyNames']
const SCHEMA_CHILD_KEYS = ['properties', ...SCHEMA_ARRAY_KEYS, ...SCHEMA_OBJECT_KEYS]

const removeSchemaChildren = (node) => {
  const clone = { ...node }
  SCHEMA_CHILD_KEYS.forEach(key => delete clone[key])
  return clone
}

export const removeSchemaRecursion = (node, ancestors = [], objectAncestors = []) => {
  if (!node || typeof node !== 'object') return node
  if (Array.isArray(node)) {
    return node.map(child => removeSchemaRecursion(child, ancestors, objectAncestors))
  }

  const identifier = node.schema$ref || node.$ref || node.schemaName || node.name
  const isRepeatedAncestor = identifier && ancestors.includes(identifier)

  if (objectAncestors.includes(node) || isRepeatedAncestor) {
    return removeSchemaChildren(node)
  }

  const nextAncestors = identifier ? [...ancestors, identifier] : ancestors
  const nextObjectAncestors = [...objectAncestors, node]
  const removeChildRecursion = child => removeSchemaRecursion(child, nextAncestors, nextObjectAncestors)
  const clone = { ...node }

  if (clone.properties) {
    clone.properties = {}
    for (const [key, value] of Object.entries(node.properties)) {
      if (!value?.deprecated) {
        clone.properties[key] = removeChildRecursion(value)
      }
    }
  }
  SCHEMA_ARRAY_KEYS.forEach(key => {
    if (Array.isArray(node[key])) {
      clone[key] = node[key].map(removeChildRecursion)
    }
  })
  SCHEMA_OBJECT_KEYS.forEach(key => {
    if (node[key] && typeof node[key] === 'object') {
      clone[key] = removeChildRecursion(node[key])
    }
  })

  return clone
}

const walkSchemaNode = (node, propName = '', processor) => {
  if (!node || typeof node !== 'object') return node
  processor(node, propName)

  if (node.properties) {
    for (const [key, value] of Object.entries(node.properties)) {
      walkSchemaNode(value, key, processor)
    }
  }
  SCHEMA_ARRAY_KEYS.forEach(key => {
    if (Array.isArray(node[key])) {
      node[key].forEach(child => walkSchemaNode(child, propName, processor))
    }
  })
  SCHEMA_OBJECT_KEYS.forEach(key => {
    if (node[key] && typeof node[key] === 'object') {
      if (Array.isArray(node[key])) {
        node[key].forEach(child => walkSchemaNode(child, propName, processor))
      } else {
        walkSchemaNode(node[key], propName, processor)
      }
    }
  })
  return node
}

const stripSchemaExamples = (node) => {
  return walkSchemaNode(node, '', (n) => {
    delete n.example
    delete n.default
  })
}

const injectMockExample = (node) => {
  return walkSchemaNode(node, '', (n, propName) => {
    if (n.example !== undefined || n.default !== undefined || n.enum) return
    const name = propName.toLowerCase()
    if (n.type === 'string' && !n.format) {
      if (name.includes('email')) n.example = Mock.mock('@email')
      else if (name.includes('phone') || name.includes('mobile')) n.example = Mock.mock(/^1[345789]\d{9}$/)
      else if (name.includes('url')) n.example = Mock.mock('@url')
      else if (name.includes('ip')) n.example = Mock.mock('@ip')
      else if (name.includes('id') || name.includes('code') || name.includes('no')) n.example = Mock.mock('@string("upper", 8, 16)')
      else if (name.includes('date') || name.includes('time')) n.example = Mock.mock('@datetime')
      else if (name.includes('company')) n.example = Mock.mock('@ctitle(4, 10)') + '公司'
      else if (name.includes('name')) n.example = Mock.mock('@cname')
      else if (name.includes('type') || name.includes('status')) n.example = Mock.mock('@integer(0, 3)').toString()
      else n.example = Mock.mock('@ctitle(3, 8)')
    } else if (n.type === 'number' || n.type === 'integer') {
      if (name.includes('type') || name.includes('status')) n.example = Mock.mock('@integer(0, 3)')
      else if (name.includes('price') || name.includes('amount') || name.includes('fee')) n.example = Mock.mock('@float(10, 1000, 2, 2)')
      else n.example = Mock.mock('@integer(1, 999)')
    } else if (n.type === 'boolean') {
      n.example = Mock.mock('@boolean')
    }
  })
}

const injectDescriptionExample = (node) => {
  return walkSchemaNode(node, '', (n) => {
    if (n.example !== undefined || n.default !== undefined || n.enum) return
    if (n.description && n.type === 'string' && !n.format) {
      n.example = n.description
    }
  })
}

const injectFakerKeywords = (node) => {
  return walkSchemaNode(node, '', (n, propName) => {
    if (n.example !== undefined || n.default !== undefined || n.enum) return
    const name = propName.toLowerCase()
    if (n.type === 'string' && !n.format && !n.faker) {
      if (name.includes('email')) n.faker = 'internet.email'
      else if (name.includes('phone') || name.includes('mobile')) n.faker = 'phone.number'
      else if (name.includes('url')) n.faker = 'internet.url'
      else if (name.includes('ip')) n.faker = 'internet.ipv4'
      else if (name.includes('id') || name.includes('code') || name.includes('no')) n.faker = 'string.alphanumeric({ length: 10 })'
      else if (name.includes('date') || name.includes('time')) n.faker = 'date.past'
      else if (name.includes('company')) n.faker = 'company.name'
      else if (name.includes('name')) n.faker = 'person.fullName'
      else if (name.includes('type') || name.includes('status')) n.faker = 'string.numeric({ length: 1 })'
    }
  })
}

export const generateSchemaSample = async (schemaBody, type, preferenceId) => {
  return showGenerateSampleWindow(schemaBody, type, preferenceId).then(async (result) => {
    const { mode, useExample, useDescription } = result
    let schema = isString(schemaBody) ? JSON.parse(schemaBody) : cloneDeep(schemaBody)
    schema = removeSchemaRecursion(schema)
    if (!useExample) {
      stripSchemaExamples(schema)
    }
    if (useDescription) {
      injectDescriptionExample(schema)
    }
    let json
    if (mode === 'ai') {
      try {
        const components = {}
        const compressNode = (node) => {
          if (!isObject(node)) return node
          if (node.schema$ref) {
            const ref = node.schema$ref
            const name = ref.split('/').pop()
            if (!components[name]) {
              components[name] = {}
              const copy = { ...node }; delete copy.schema$ref; delete copy.name; delete copy.isLeaf; delete copy.__contentType; delete copy.__id
              components[name] = compressNode(copy)
            }
            return { $ref: ref }
          }
          if (isArray(node)) return node.map(compressNode)
          const res = {}
          for (const k in node) {
            if (k === 'isLeaf' || k === '__contentType' || k === 'name' || k === '__id') continue
            res[k] = compressNode(node[k])
          }
          return res
        }
        const payload = JSON.stringify({
          schema: compressNode(schema),
          components: { schemas: components }
        })
        const res = await aiGenerateSample({ schemaContent: payload }, { loading: true, timeout: 125000, preferenceId, showErrorMessage: false }).catch(err => err?.data)
        if (res && res.code === 202) {
          ElMessage.warning(res.message || $i18nMsg('已加入请求队列，请稍后再次生成', 'Request queued, please generate again later'))
          json = { message: res.message || $i18nMsg('已加入请求队列，请稍后再次生成', 'Request queued, please generate again later') }
        } else if (res && res.success === false) {
          json = { error: $i18nMsg('AI 生成失败', 'AI generation failed'), details: res.message || $i18nMsg('未知错误', 'Unknown error') }
        } else if (res && res.resultData) {
          try {
            json = JSON.parse(res.resultData)
          } catch (e) {
            console.error('AI返回的格式不是合法的JSON', e)
            json = { error: $i18nMsg('AI 生成失败或返回非法数据', 'AI generation failed or returned invalid data'), details: res.resultData }
          }
        } else {
          json = { error: $i18nMsg('AI 生成接口返回为空', 'AI generation interface returned empty'), details: res ? JSON.stringify(res) : $i18nMsg('无详细信息', 'No details') }
        }
      } catch (err) {
        console.error('AI接口调用异常', err)
        json = { error: $i18nMsg('调用 AI 接口失败', 'Failed to call AI interface'), details: err?.data?.message || err?.message || $i18nMsg('未知错误', 'Unknown error') }
      }
    } else if (mode === 'mockjs') {
      schema = injectMockExample(schema)
      json = sample(schema)
    } else if (mode === 'json-schema-faker') {
      schema = injectFakerKeywords(schema)
      json = await generate(schema, {
        alwaysFakeOptionals: true,
        useExamplesValue: useExample,
        useDefaultValue: useExample,
        fillProperties: false,
        extensions: { faker }
      })
    } else {
      json = sample(schema)
    }
    console.log('===========================schema mode:', mode, schema)
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
      resStr = JSON.stringify(json, null, 2)
    }
    return resStr
  })
}
/**
 * 显示示例
 *
 * @param requestsSchema
 * @param componentMap
 * @param config
 * @returns {Promise<void>}
 */
export const showGenerateSchemaSample = async (requestsSchema, componentMap, config) => {
  let sampleStr = ''
  if (!config?.forceGenerate && requestsSchema.examples) {
    const examples = JSON.parse(requestsSchema.examples)
    if (config?.selectedExample) {
      sampleStr = config.selectedExample.value && JSON.stringify(config.selectedExample.value, null, 2)
    } else {
      sampleStr = examples?.[0]?.value && JSON.stringify(examples[0].value, null, 2)
    }
  }
  if (!sampleStr) {
    const generateSchema = processSchemas(requestsSchema, componentMap, true)
    console.log('===================generateSchema', generateSchema)
    if (generateSchema[0]?.schema) {
      sampleStr = await generateSchemaSample(generateSchema[0].schema, requestsSchema.contentType, config?.preferenceId)
    }
  }
  if (sampleStr) {
    const windowConfig = { readOnly: false, viewAsTable: true, ...config }
    if (!window.location.href.includes('/share') && requestsSchema.id) {
      const currentContent = { value: sampleStr }
      windowConfig.change = (text) => { currentContent.value = text }
      windowConfig.buttons = [{
        label: $i18nKey('common.label.commonSave', 'common.label.example'),
        type: 'warning',
        click () {
          $corePrompt($i18nBundle('common.msg.commonInput', [$i18nBundle('common.label.example')]),
            $i18nKey('common.label.commonSave', 'common.label.example'), {
              inputValue: config?.selectedExample?.summary || 'Custom Example'
            }).then(({ value }) => {
            if (!value) return
            const newExamples = requestsSchema.examples ? cloneDeep(JSON.parse(requestsSchema.examples)) : []
            // Try to find by original name first (if editing an existing example)
            let existingIndex = -1
            if (config?.selectedExample?.summary) {
              existingIndex = newExamples.findIndex(e => e.summary === config.selectedExample.summary)
            }
            // If not found by original name (e.g. force generate), try to find by the new name
            if (existingIndex < 0) {
              existingIndex = newExamples.findIndex(e => e.summary === value)
            }
            let exampleValue = currentContent.value
            try {
              exampleValue = JSON.parse(exampleValue)
            } catch {
              // Ignore invalid json
            }
            const newExample = { summary: value, value: exampleValue }
            if (existingIndex >= 0) {
              newExamples.splice(existingIndex, 1, newExample)
            } else {
              newExamples.push(newExample)
            }
            updateExamples({ id: requestsSchema.id, examples: JSON.stringify(newExamples) }).then(res => {
              if (res.success) {
                $coreSuccess($i18nBundle('common.msg.saveSuccess'))
                requestsSchema.examples = JSON.stringify(newExamples)
              }
            })
          }).catch(() => {})
        }
      }, ...(config.buttons || [])]
    }
    return showCodeWindow(sampleStr, windowConfig)
  }
}

export const getParsedExamples = (schema) => {
  if (schema?.examples) {
    try {
      const parsed = JSON.parse(schema.examples)
      return Array.isArray(parsed) ? parsed : [parsed]
    } catch {
      // ignore
    }
  }
  return []
}

export const doDeleteExample = (schema, example, index) => {
  $coreConfirm($i18nBundle('common.msg.commonDeleteConfirm', [example.summary])).then(() => {
    const newExamples = cloneDeep(getParsedExamples(schema))
    newExamples.splice(index, 1)
    updateExamples({ id: schema.id, examples: JSON.stringify(newExamples) }).then(res => {
      if (res.success) {
        $coreSuccess($i18nBundle('common.msg.deleteSuccess'))
        schema.examples = JSON.stringify(newExamples)
      }
    })
  }).catch(() => {})
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
