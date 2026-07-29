import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 分享相关store
 */
export const useShareConfigStore = defineStore('shareConfigStore', () => {
  const shareConfig = ref({})
  const sharePreferenceView = ref({})
  const shareParamTargets = ref({})
  const shareGenerateCodeConfig = ref({})
  const extractedEnvParams = ref({})
  const localEnvParams = ref({})

  const clearShareToken = (shareId) => {
    delete shareConfig.value[shareId]
    clearSharePreference(shareId)
  }
  const clearSharePreference = (shareId) => {
    delete extractedEnvParams.value[shareId]
    delete localEnvParams.value[shareId]
    delete sharePreferenceView.value[shareId]
    delete shareGenerateCodeConfig.value[shareId]
    Object.keys(shareParamTargets.value).forEach(key => {
      if (key.startsWith(shareId)) {
        delete shareParamTargets.value[key]
      }
    })
  }

  return {
    shareConfig,
    sharePreferenceView,
    shareParamTargets,
    shareGenerateCodeConfig,
    extractedEnvParams,
    localEnvParams,
    getShareToken (shareId) {
      return shareConfig.value[shareId]
    },
    setShareToken (shareId, token) {
      shareConfig.value[shareId] = token
    },
    clearShareToken,
    clearSharePreference,
    saveLocalEnvParams (preferenceId, params) {
      if (preferenceId) {
        localEnvParams.value[preferenceId] = params
      }
    },
    getLocalEnvParams (preferenceId) {
      return (preferenceId && localEnvParams.value[preferenceId]) || []
    },
    resetLocalEnvParams (preferenceId) {
      if (preferenceId) {
        delete localEnvParams.value[preferenceId]
        delete extractedEnvParams.value[preferenceId]
      }
    },
    clearAllShareToken: () => {
      shareConfig.value = {}
      sharePreferenceView.value = {}
      shareParamTargets.value = {}
      shareGenerateCodeConfig.value = {}
      extractedEnvParams.value = {}
      localEnvParams.value = {}
    }
  }
}, {
  // persist: {
  //   paths: ['shareConfig', 'sharePreferenceView']
  // }
  persist: true
})
