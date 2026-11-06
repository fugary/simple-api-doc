import { inject } from 'vue'
import { useRoute } from 'vue-router'
import ApiDocApi from '@/api/ApiDocApi'
import { draftFields } from '@/services/api/DocDraftStorage'
import { useEditDraft } from '@/hooks/useEditDraft'

export const DOC_EDIT_GUARD = Symbol('doc-edit-guard')

export const useDocDraft = (currentDoc, emit) => {
  const route = useRoute()
  return useEditDraft(currentDoc, {
    type: 'doc',
    fields: draftFields,
    project: doc => doc.projectCode || route.params.projectCode,
    async load (doc) {
      let value = { ...doc }
      if (doc.id) {
        const result = await ApiDocApi.getById(doc.id)
        if (!result.success || !result.resultData) throw new Error('Document unavailable')
        value = { ...doc, ...result.resultData }
      }
      if (!value.docContent && value.docType === 'api') value.docContent = value.description || ''
      return value
    },
    applied: doc => { doc.label = doc.docName },
    saved: doc => emit('savedDoc', doc)
  }, inject(DOC_EDIT_GUARD, null))
}
