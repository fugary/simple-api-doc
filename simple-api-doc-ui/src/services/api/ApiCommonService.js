import { $coreConfirm, getSingleSelectOptions } from '@/utils'
import { $i18nKey } from '@/messages'
import { sample } from 'openapi-sampler'
import { XMLBuilder } from 'fast-xml-parser'
import { cloneDeep, isArray, isFunction, isString } from 'lodash-es'
import { ALL_CONTENT_TYPES } from '@/consts/ApiConstants'
import { useMediaQuery } from '@vueuse/core'
import { removeSchemaDeprecated } from '@/services/api/ApiDocPreviewService'

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

export const calcSuggestionsFunc = (keySuggestions) => {
  if (isFunction(keySuggestions)) {
    return keySuggestions
  } else if (isArray(keySuggestions)) {
    return (queryString, cb) => {
      const dataList = keySuggestions.filter(item => item.toLowerCase().includes(queryString?.toLowerCase()))
        .map(value => ({ value }))
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

export const processEvnParams = (groupConfig, dataValue) => {
  if (groupConfig && isString(dataValue) && dataValue.includes('{{') && dataValue.includes('}}')) {
    groupConfig = groupConfig && isString(groupConfig) ? JSON.parse(groupConfig) : groupConfig
    if (groupConfig?.envParams?.length) {
      groupConfig?.envParams.filter(param => param.enabled && param.name && isString(param.value)).forEach(item => {
        dataValue = dataValue.replace(`{{${item.name}}}`, (item.value || '').trim())
      })
    }
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
