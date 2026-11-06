import { defineStore } from 'pinia'
import { draftKey, readDraft, writeDraft, removeDraft } from '@/services/api/DocDraftStorage'

// 按条目读写，不持久化整个 Store，也不在内存中重复缓存大型正文。
export const useEditDraftStore = defineStore('editDraft', () => ({
  key: draftKey,
  read: (key, type) => readDraft(window.localStorage, key, Date.now(), type),
  write: (key, fields, base, version, type) => writeDraft(window.localStorage, key, fields, base, version, type),
  remove: (key, expected) => removeDraft(window.localStorage, key, expected)
}))
