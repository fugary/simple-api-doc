import { ref } from 'vue'
import { defineStore } from 'pinia'
import { isMarkdownDoc } from '@/services/api/ApiProjectService'

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
    recordRecentDoc (preferenceId, doc) {
      if (!preferenceId || !doc?.id || !doc?.isDoc) return
      const pref = sharePreferenceView.value[preferenceId] = sharePreferenceView.value[preferenceId] || {}
      const list = pref.recentDocs || []
      const docId = doc.id
      const isMd = isMarkdownDoc(doc)
      if (list[0]?.id === docId) {
        list[0].accessTime = Date.now()
        list[0].docName = doc.docName || doc.label || list[0].docName
        list[0].url = doc.url || list[0].url
        list[0].method = doc.method || list[0].method
        list[0].docType = isMd ? 'md' : 'api'
        list[0].deprecated = !!doc.deprecated
        return
      }
      const filtered = list.filter(item => item.id !== docId)
      filtered.unshift({
        id: docId,
        docName: doc.docName || doc.label,
        docType: isMd ? 'md' : 'api',
        method: doc.method,
        url: doc.url,
        deprecated: !!doc.deprecated,
        accessTime: Date.now()
      })
      pref.recentDocs = filtered.slice(0, 10)
    },
    removeRecentDoc (preferenceId, docId) {
      if (!preferenceId) return
      const pref = sharePreferenceView.value[preferenceId]
      if (pref?.recentDocs) {
        pref.recentDocs = pref.recentDocs.filter(item => item.id !== docId)
      }
    },
    clearRecentDocs (preferenceId) {
      if (!preferenceId) return
      const pref = sharePreferenceView.value[preferenceId]
      if (pref) {
        pref.recentDocs = []
      }
    },
    getRecentDocs (preferenceId) {
      return (preferenceId && sharePreferenceView.value[preferenceId]?.recentDocs) || []
    },
    pushNavDoc (preferenceId, docId) {
      if (!preferenceId || !docId) return
      if (isNavigatingHistory.value) {
        isNavigatingHistory.value = false
        return
      }
      const pref = sharePreferenceView.value[preferenceId] = sharePreferenceView.value[preferenceId] || {}
      let stack = pref.navStack || []
      const index = pref.navIndex ?? -1

      if (index >= 0 && stack[index] === docId) {
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
    navBack (preferenceId) {
      if (!preferenceId) return null
      const pref = sharePreferenceView.value[preferenceId]
      if (!pref || !pref.navStack?.length) return null
      if (pref.navIndex > 0) {
        pref.navIndex--
        isNavigatingHistory.value = true
        return pref.navStack[pref.navIndex]
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
        return pref.navStack[pref.navIndex]
      }
      return null
    },
    cleanNavStack (preferenceId, validIds) {
      if (!preferenceId || !validIds) return
      const pref = sharePreferenceView.value[preferenceId]
      if (!pref?.navStack?.length) return
      const validSet = validIds instanceof Set ? validIds : new Set(validIds)
      const currentDocId = pref.navIndex >= 0 ? pref.navStack[pref.navIndex] : null
      const newStack = pref.navStack.filter(id => validSet.has(id))
      if (newStack.length !== pref.navStack.length) {
        pref.navStack = newStack
        if (currentDocId && newStack.includes(currentDocId)) {
          pref.navIndex = newStack.indexOf(currentDocId)
        } else {
          pref.navIndex = newStack.length - 1
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
