<script setup>
import { computed } from 'vue'
import { useDocDraft } from '@/hooks/useDocDraft'
import DocEditorLayout from '@/views/components/api/doc/comp/DocEditorLayout.vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { uploadFiles } from '@/api/ApiProjectApi'

import { useRoute } from 'vue-router'

const route = useRoute()
defineProps({ projectItem: { type: Object, default: undefined } })
const currentDoc = defineModel({
  type: Object,
  default: () => ({})
})
const emit = defineEmits(['savedDoc'])
const { model: currentDocModel, saving, draft, onSaved } = useDocDraft(currentDoc, emit)
const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')
const onUploadImg = (files, callback) => {
  const projectCode = route?.params?.projectCode || currentDocModel.value?.projectCode || currentDoc.value?.projectCode
  uploadFiles(files, callback, projectCode)
}

</script>
<template>
  <doc-editor-layout
    v-model="currentDoc"
    v-model:doc-model="currentDocModel"
    v-model:saving="saving"
    :draft="draft"
    :project-item="projectItem"
    @saved-doc="onSaved"
  >
    <md-editor
      v-model="currentDocModel.docContent"
      :theme="theme"
      class="scroll-main-container"
      :on-upload-img="onUploadImg"
    />
  </doc-editor-layout>
</template>
