<script setup>
import { computed, ref, watch } from 'vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import MarkdownDocEditHeader from '@/views/components/api/doc/comp/MarkdownDocEditHeader.vue'
import { uploadFiles } from '@/api/ApiProjectApi'
import { $coreHideLoading, $coreShowLoading } from '@/utils'
import ApiDocApi from '@/api/ApiDocApi'

const currentDoc = defineModel({
  type: Object,
  default: () => ({})
})
const currentDocModel = ref({
  ...currentDoc.value
})
const loadCurrentDoc = (id) => {
  currentDocModel.value && (currentDocModel.value.docContent = '')
  $coreShowLoading({ delay: 0 })
  ApiDocApi.getById(id).then(data => {
    Object.assign(currentDocModel.value, data.resultData)
    $coreHideLoading()
  }).catch(() => $coreHideLoading())
}
watch(currentDoc, (newDoc) => {
  if (newDoc.id) {
    loadCurrentDoc(currentDoc.value.id)
  } else {
    currentDocModel.value = {
      ...newDoc
    }
  }
}, { immediate: true })
const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')
defineEmits(['savedDoc'])
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
      :on-upload-img="uploadFiles"
    />
  </el-container>
</template>

<style scoped>

</style>
