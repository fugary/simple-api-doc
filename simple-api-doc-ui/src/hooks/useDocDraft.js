import { computed, inject, onActivated, onBeforeUnmount, onDeactivated, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import ApiDocApi from '@/api/ApiDocApi'
import { useLoginConfigStore } from '@/stores/LoginConfigStore'
import { draftFields, draftKey, readDraft, writeDraft, removeDraft } from '@/services/api/DocDraftStorage'

export const DOC_EDIT_GUARD = Symbol('doc-edit-guard')

export const useDocDraft = (currentDoc, emit) => {
  const route = useRoute()
  const login = useLoginConfigStore()
  const guard = inject(DOC_EDIT_GUARD, null)
  const model = ref()
  const base = ref()
  const pending = ref()
  const saving = ref(false)
  const error = ref(false)
  const saved = ref(false)
  const loading = ref(false)
  const loadError = ref(false)
  const leaving = ref(false)
  let key
  let storedDraft
  let timer
  let sequence = 0
  let active = true
  let confirmPromise
  let finishLeave
  const fields = computed(() => draftFields(model.value))
  const dirty = computed(() => !!base.value && JSON.stringify(fields.value) !== JSON.stringify(base.value))
  const conflict = computed(() => pending.value && (JSON.stringify(pending.value.base) !== JSON.stringify(base.value) || pending.value.version !== model.value?.version))
  const persist = () => {
    clearTimeout(timer)
    if (!base.value || pending.value || (!dirty.value && !storedDraft)) return
    try {
      if (dirty.value) {
        storedDraft = writeDraft(window.localStorage, key, model.value, base.value, model.value.version)
        saved.value = true
      } else if (storedDraft) {
        removeDraft(window.localStorage, key, storedDraft)
        storedDraft = undefined
        saved.value = false
      }
      error.value = false
    } catch { error.value = true }
  }
  const confirmLeave = () => {
    if (saving.value) return Promise.resolve(false)
    if (!dirty.value) return Promise.resolve(true)
    if (confirmPromise) return confirmPromise
    persist()
    confirmPromise = new Promise(resolve => { finishLeave = resolve })
    leaving.value = true
    return confirmPromise
  }
  const load = async (doc) => {
    persist()
    const request = ++sequence
    base.value = undefined
    model.value = undefined
    pending.value = undefined
    storedDraft = undefined
    error.value = saved.value = loadError.value = false
    loading.value = false
    if (!doc) return
    const userId = login.accountInfo?.id
    const system = login.systemKey
    const project = doc.projectCode || route.params.projectCode
    loading.value = true
    try {
      let value = { ...doc }
      if (doc.id) {
        const result = await ApiDocApi.getById(doc.id)
        if (!result.success || !result.resultData) throw new Error('Document unavailable')
        value = { ...doc, ...result.resultData }
      }
      if (request !== sequence) return
      if (userId !== login.accountInfo?.id || system !== login.systemKey) throw new Error('Account changed')
      if (!value.docContent && value.docType === 'api') value.docContent = value.description || ''
      model.value = value
      base.value = draftFields(value)
      key = draftKey(system, userId, project, value)
      try { pending.value = storedDraft = readDraft(window.localStorage, key) } catch { error.value = true }
    } catch {
      if (request === sequence) loadError.value = true
    } finally {
      if (request === sequence) loading.value = false
    }
  }
  watch(currentDoc, load, { immediate: true })
  watch(fields, () => {
    clearTimeout(timer)
    saved.value = false
    if (base.value && !pending.value) timer = setTimeout(persist, 500)
  })
  const restore = () => {
    Object.assign(model.value, pending.value.fields)
    model.value.label = model.value.docName
    pending.value = undefined
    persist()
  }
  const discard = () => {
    try {
      if (storedDraft) removeDraft(window.localStorage, key, storedDraft)
      storedDraft = undefined
      Object.assign(model.value, base.value)
      model.value.label = model.value.docName
      pending.value = undefined
      saved.value = error.value = false
      return true
    } catch {
      error.value = true
      return false
    }
  }
  const resolveLeave = action => {
    if (action === 'discard' && !discard()) return
    if (action === 'keep') {
      persist()
      if (error.value) return
    }
    leaving.value = false
    finishLeave?.(action !== 'stay')
    finishLeave = confirmPromise = null
  }
  const onSaved = (doc) => {
    clearTimeout(timer)
    base.value = draftFields(model.value)
    pending.value = undefined
    persist()
    emit('savedDoc', doc)
  }
  const beforeUnload = event => {
    if (!active || !dirty.value) return
    persist()
    event.preventDefault()
    event.returnValue = ''
  }
  const onHidden = () => { if (active && document.hidden) persist() }
  window.addEventListener('beforeunload', beforeUnload)
  document.addEventListener('visibilitychange', onHidden)
  if (guard) guard.value = confirmLeave
  onBeforeRouteLeave(confirmLeave)
  onBeforeRouteUpdate(confirmLeave)
  onActivated(() => { active = true; if (guard) guard.value = confirmLeave })
  onDeactivated(() => {
    persist()
    active = false
    if (guard?.value === confirmLeave) guard.value = null
  })
  onBeforeUnmount(() => {
    resolveLeave('stay')
    persist()
    sequence++
    window.removeEventListener('beforeunload', beforeUnload)
    document.removeEventListener('visibilitychange', onHidden)
    if (guard?.value === confirmLeave) guard.value = null
  })
  return {
    model,
    saving,
    onSaved,
    draft: reactive({ pending, conflict, dirty, saved, error, loading, loadError, leaving, resolveLeave, restore, discard, confirmLeave, retry: () => load(currentDoc.value) })
  }
}
