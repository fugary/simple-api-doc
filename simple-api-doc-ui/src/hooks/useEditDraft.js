import { computed, onActivated, onBeforeUnmount, onDeactivated, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useLoginConfigStore } from '@/stores/LoginConfigStore'
import { useEditDraftStore } from '@/stores/EditDraftStore'

export const useEditDraft = (currentDoc, adapter, guard = null) => {
  const store = useEditDraftStore()
  const login = useLoginConfigStore()
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
  const signature = adapter.signature || JSON.stringify
  const fields = computed(() => adapter.fields(model.value))
  const baseSignature = computed(() => signature(base.value))
  const dirty = computed(() => !!base.value && signature(fields.value) !== baseSignature.value)
  const conflict = computed(() => pending.value && (signature(pending.value.base) !== baseSignature.value || pending.value.version !== model.value?.version))
  const persist = () => {
    clearTimeout(timer)
    if (!base.value || pending.value || (!dirty.value && !storedDraft)) return
    try {
      if (dirty.value) {
        storedDraft = store.write(key, fields.value, base.value, model.value.version, adapter.type)
        saved.value = true
      } else if (storedDraft) {
        store.remove(key, storedDraft)
        storedDraft = undefined
        saved.value = false
      }
      error.value = false
    } catch { error.value = true }
  }
  const confirmLeave = () => {
    if (!active) return Promise.resolve(true)
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
    loading.value = true
    try {
      const value = await adapter.load(doc)
      if (request !== sequence) return
      if (userId !== login.accountInfo?.id || system !== login.systemKey) throw new Error('Account changed')
      model.value = value
      adapter.loaded?.(value)
      if (adapter.enabled && !adapter.enabled(value)) return
      base.value = adapter.fields(value)
      key = store.key(system, userId, adapter.project(value), value, adapter.type)
      try { pending.value = storedDraft = store.read(key, adapter.type) } catch { error.value = true }
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
    adapter.applied?.(model.value)
    pending.value = undefined
    persist()
  }
  const discard = () => {
    try {
      if (storedDraft) store.remove(key, storedDraft)
      storedDraft = undefined
      Object.assign(model.value, base.value)
      adapter.applied?.(model.value)
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
    if (base.value) base.value = adapter.fields(model.value)
    pending.value = undefined
    persist()
    adapter.saved?.(doc)
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
  // 动态挂载的编辑弹窗不属于 RouterView，也需要离开保护。
  const removeRouteGuard = useRouter().beforeEach(confirmLeave)
  onActivated(() => { active = true; if (guard) guard.value = confirmLeave })
  onDeactivated(() => {
    persist()
    active = false
    if (guard?.value === confirmLeave) guard.value = null
  })
  onBeforeUnmount(() => {
    resolveLeave('stay')
    removeRouteGuard()
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
