import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 分享相关store
 */
export const useShareConfigStore = defineStore('shareConfigStore', () => {
  const shareConfig = ref({})
  const shareFolderView = ref({})
  return {
    shareConfig,
    shareFolderView,
    getShareToken (shareId) {
      return shareConfig.value[shareId]
    },
    setShareToken (shareId, token) {
      shareConfig.value[shareId] = token
    },
    clearShareToken (shareId) {
      delete shareConfig.value[shareId]
    },
    clearAllShareToken: () => {
      shareConfig.value = {}
    }
  }
}, {
  persist: true
})
