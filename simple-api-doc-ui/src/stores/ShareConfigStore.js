import { ref } from 'vue'
import { defineStore } from 'pinia'
export const useShareConfigStore = defineStore('shareConfigStore', () => {
  const shareConfig = ref({})
  return {
    setShareToken (shareId, token) {
      shareConfig[shareId] = token
    },
    clearShareToken (shareId) {
      delete shareConfig[shareId]
    },
    clearAllShareToken: () => {
      shareConfig.value = {}
    }
  }
}, {
  persist: true
})
