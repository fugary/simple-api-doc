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

  const clearShareToken = (shareId) => {
    delete shareConfig.value[shareId]
    clearSharePreference(shareId)
  }
  const clearSharePreference = (shareId) => {
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
    getShareToken (shareId) {
      return shareConfig.value[shareId]
    },
    setShareToken (shareId, token) {
      shareConfig.value[shareId] = token
    },
    clearShareToken,
    clearSharePreference,
    clearAllShareToken: () => {
      shareConfig.value = {}
      sharePreferenceView.value = {}
      shareParamTargets.value = {}
      shareGenerateCodeConfig.value = {}
    }
  }
}, {
  // persist: {
  //   paths: ['shareConfig', 'sharePreferenceView']
  // }
  persist: true
})
