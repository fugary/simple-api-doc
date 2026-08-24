import { defineStore } from 'pinia'
import { ref } from 'vue'
import { isObject } from 'lodash-es'

/**
 * AI 配置与模型缓存 Store
 */
export const useAiConfigStore = defineStore('aiConfig', () => {
  const cachedModels = ref({})

  /**
   * 根据 config 对象计算 URL + APIKey 特征 Key
   * @param {Object} config
   * @return {string}
   */
  const calcFeaturesKey = (config) => {
    if (isObject(config) && config.baseUrl) {
      return `${config.provider || 'OPENAI'}@${config.baseUrl}@${config.apiKey || ''}`
    }
    return ''
  }

  /**
   * 根据 configId 或 config 对象计算主要 Key
   * @param {number|string|Object} configOrId
   * @return {string}
   */
  const calcConfigKey = (configOrId) => {
    if (!configOrId) return ''
    if (typeof configOrId === 'number' || typeof configOrId === 'string') {
      return `id:${configOrId}`
    }
    if (isObject(configOrId)) {
      if (configOrId.id) {
        return `id:${configOrId.id}`
      }
      return calcFeaturesKey(configOrId)
    }
    return ''
  }

  /**
   * 获取缓存的模型名称列表
   * @param {number|string|Object} configOrId
   * @return {Array<string>}
   */
  const getCachedModels = (configOrId) => {
    const key = calcConfigKey(configOrId)
    if (key && Array.isArray(cachedModels.value[key]) && cachedModels.value[key].length > 0) {
      return cachedModels.value[key]
    }
    if (isObject(configOrId)) {
      const featKey = calcFeaturesKey(configOrId)
      if (featKey && Array.isArray(cachedModels.value[featKey]) && cachedModels.value[featKey].length > 0) {
        return cachedModels.value[featKey]
      }
    }
    return []
  }

  /**
   * 存储模型名称列表到缓存中
   * @param {number|string|Object} configOrId
   * @param {Array<string>} models
   */
  const saveCachedModels = (configOrId, models) => {
    if (!Array.isArray(models)) return
    const key = calcConfigKey(configOrId)
    if (key) {
      cachedModels.value[key] = models
    }
    if (isObject(configOrId)) {
      const featKey = calcFeaturesKey(configOrId)
      if (featKey) {
        cachedModels.value[featKey] = models
      }
    }
  }

  /**
   * 清理模型缓存
   * @param {number|string|Object} [configOrId] 若不传则清空全部
   */
  const clearCachedModels = (configOrId) => {
    if (!configOrId) {
      cachedModels.value = {}
      return
    }
    const key = calcConfigKey(configOrId)
    if (key) {
      delete cachedModels.value[key]
    }
    if (isObject(configOrId)) {
      const featKey = calcFeaturesKey(configOrId)
      if (featKey) {
        delete cachedModels.value[featKey]
      }
    }
  }

  return {
    cachedModels,
    calcConfigKey,
    calcFeaturesKey,
    getCachedModels,
    saveCachedModels,
    clearCachedModels
  }
}, {
  persist: true
})
