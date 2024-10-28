import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 分享相关store
 */
export const useShareConfigStore = defineStore('shareConfigStore', () => {
  const shareConfig = ref({})
  const sharePreferenceView = ref({})
  const shareParamTargets = ref({})

  const clearShareToken = (shareId) => {
    delete shareConfig.value[shareId]
    clearSharePreference(shareId)
  }
  const clearSharePreference = (shareId) => {
    delete sharePreferenceView.value[shareId]
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
    }
  }
}, {
  // persist: {
  //   paths: ['shareConfig', 'sharePreferenceView']
  // }
  persist: true
})
