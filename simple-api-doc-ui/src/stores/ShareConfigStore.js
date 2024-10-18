import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 分享相关store
 */
export const useShareConfigStore = defineStore('shareConfigStore', () => {
  const shareConfig = ref({})
  const sharePreferenceView = ref({})
  const shareParamTargets = ref({})
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
    clearShareToken (shareId) {
      delete shareConfig.value[shareId]
      delete sharePreferenceView.value[shareId]
      Object.keys(shareParamTargets.value).forEach(key => {
        if (key.startsWith(shareId)) {
          delete shareParamTargets.value[key]
        }
      })
    },
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
