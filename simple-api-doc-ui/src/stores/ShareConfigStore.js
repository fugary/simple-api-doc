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
    },
    clearAllShareToken: () => {
      shareConfig.value = {}
      sharePreferenceView.value = {}
    }
  }
}, {
  persist: {
    paths: ['shareConfig', 'sharePreferenceView']
  }
})
