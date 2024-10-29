<script setup>
import { computed, ref } from 'vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import MarkdownDocEditHeader from '@/views/components/api/doc/comp/MarkdownDocEditHeader.vue'
import { $httpPost } from '@/vendors/axios'

const currentDoc = defineModel({
  type: Object,
  default: () => ({})
})
const currentDocModel = ref({
  ...currentDoc.value
})
const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')
defineEmits(['savedDoc'])
const onUploadImg = (files, callback) => {
  const formData = new FormData()
  files.forEach(file => formData.append('files', file))
  $httpPost('/upload/uploadFiles', formData,
    Object.assign({ headers: { 'Content-Type': 'multipart/form-data' }, loading: true, timeout: 60000 }))
    .then(data => {
      data.success && callback(data.resultData)
    })
}
</script>
<template>
  <el-container class="flex-column padding-left2 height100">
    <markdown-doc-edit-header
      v-model="currentDoc"
      v-model:doc-model="currentDocModel"
      @saved-doc="$emit('savedDoc', $event)"
    />
    <md-editor
      v-model="currentDocModel.docContent"
      :theme="theme"
      class="scroll-main-container"
      :on-upload-img="onUploadImg"
    />
  </el-container>
</template>

<style scoped>

</style>
