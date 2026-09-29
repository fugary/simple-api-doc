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
  const isNavigatingHistory = ref(false)

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
    pushNavDoc (preferenceId, doc) {
      if (!preferenceId || !doc) return
      if (isNavigatingHistory.value) {
        isNavigatingHistory.value = false
        return
      }
      const docId = typeof doc === 'object' ? doc.id : doc
      if (!docId) return

      const pref = sharePreferenceView.value[preferenceId] = sharePreferenceView.value[preferenceId] || {}
      let stack = pref.navStack || []
      const index = pref.navIndex ?? -1

      const currentEntry = index >= 0 ? stack[index] : null
      const currentId = typeof currentEntry === 'object' ? currentEntry?.id : currentEntry
      if (currentId === docId) {
        return
      }

      if (index >= 0 && index < stack.length - 1) {
        stack = stack.slice(0, index + 1)
      }

      stack.push(docId)

      const MAX_DEPTH = 50
      if (stack.length > MAX_DEPTH) {
        stack = stack.slice(stack.length - MAX_DEPTH)
      }

      pref.navStack = stack
      pref.navIndex = stack.length - 1
    },
    getBackHistory (preferenceId) {
      if (!preferenceId) return []
      const pref = sharePreferenceView.value[preferenceId]
      if (!pref || !pref.navStack?.length) return []
      const index = pref.navIndex ?? -1
      if (index <= 0) return []
      const result = []
      for (let i = index - 1; i >= 0; i--) {
        const item = pref.navStack[i]
        if (item != null) {
          result.push({
            id: typeof item === 'object' ? item.id : item,
            index: i
          })
        }
      }
      return result
    },
    getForwardHistory (preferenceId) {
      if (!preferenceId) return []
      const pref = sharePreferenceView.value[preferenceId]
      if (!pref || !pref.navStack?.length) return []
      const index = pref.navIndex ?? -1
      if (index < 0 || index >= pref.navStack.length - 1) return []
      const result = []
      for (let i = index + 1; i < pref.navStack.length; i++) {
        const item = pref.navStack[i]
        if (item != null) {
          result.push({
            id: typeof item === 'object' ? item.id : item,
            index: i
          })
        }
      }
      return result
    },
    navToIndex (preferenceId, targetIndex) {
      if (!preferenceId) return null
      const pref = sharePreferenceView.value[preferenceId]
      if (!pref || !pref.navStack?.length) return null
      if (targetIndex >= 0 && targetIndex < pref.navStack.length) {
        pref.navIndex = targetIndex
        isNavigatingHistory.value = true
        const entry = pref.navStack[targetIndex]
        return typeof entry === 'object' ? entry.id : entry
      }
      return null
    },
    navBack (preferenceId) {
      if (!preferenceId) return null
      const pref = sharePreferenceView.value[preferenceId]
      if (!pref || !pref.navStack?.length) return null
      if (pref.navIndex > 0) {
        pref.navIndex--
        isNavigatingHistory.value = true
        const entry = pref.navStack[pref.navIndex]
        return typeof entry === 'object' ? entry.id : entry
      }
      return null
    },
    navForward (preferenceId) {
      if (!preferenceId) return null
      const pref = sharePreferenceView.value[preferenceId]
      if (!pref || !pref.navStack?.length) return null
      if (pref.navIndex >= 0 && pref.navIndex < pref.navStack.length - 1) {
        pref.navIndex++
        isNavigatingHistory.value = true
        const entry = pref.navStack[pref.navIndex]
        return typeof entry === 'object' ? entry.id : entry
      }
      return null
    },
    removeNavIndex (preferenceId, targetIndex) {
      if (!preferenceId) return
      const pref = sharePreferenceView.value[preferenceId]
      if (!pref || !pref.navStack?.length) return
      if (targetIndex < 0 || targetIndex >= pref.navStack.length) return

      const stack = [...pref.navStack]
      stack.splice(targetIndex, 1)
      pref.navStack = stack

      if (stack.length === 0) {
        pref.navIndex = -1
      } else if (targetIndex < pref.navIndex) {
        pref.navIndex--
      } else if (targetIndex === pref.navIndex) {
        pref.navIndex = Math.min(pref.navIndex, stack.length - 1)
      }
    },
    clearNavStack (preferenceId) {
      if (!preferenceId) return
      const pref = sharePreferenceView.value[preferenceId]
      if (pref) {
        pref.navStack = []
        pref.navIndex = -1
      }
    },
    cleanNavStack (preferenceId, validIds) {
      if (!preferenceId || !validIds) return
      const pref = sharePreferenceView.value[preferenceId]
      if (!pref?.navStack?.length) return
      const validSet = validIds instanceof Set ? validIds : new Set(validIds)
      const currentEntry = pref.navIndex >= 0 ? pref.navStack[pref.navIndex] : null
      const currentDocId = typeof currentEntry === 'object' ? currentEntry?.id : currentEntry

      const newStack = pref.navStack.filter(item => {
        const id = typeof item === 'object' ? item.id : item
        return validSet.has(id)
      })
      if (newStack.length !== pref.navStack.length) {
        pref.navStack = newStack
        const foundIndex = newStack.findIndex(item => (typeof item === 'object' ? item.id : item) === currentDocId)
        if (foundIndex >= 0) {
          pref.navIndex = foundIndex
        } else {
          pref.navIndex = newStack.length > 0 ? newStack.length - 1 : -1
        }
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
