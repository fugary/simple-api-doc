<script setup>
import { computed, ref } from 'vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import MarkdownDocEditHeader from '@/views/components/api/doc/comp/MarkdownDocEditHeader.vue'
import { useFolderLayoutHeight } from '@/services/api/ApiFolderService'

const currentDoc = defineModel({
  type: Object,
  default: () => ({})
})
const currentDocModel = ref({
  ...currentDoc.value
})
const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')
const folderContainerHeight = useFolderLayoutHeight(true, 40)
defineEmits(['savedDoc'])
</script>
<template>
  <el-container class="flex-column padding-left2">
    <markdown-doc-edit-header
      v-model="currentDoc"
      v-model:doc-model="currentDocModel"
      @saved-doc="$emit('savedDoc', $event)"
    />
    <md-editor
      v-model="currentDocModel.docContent"
      :style="{height:folderContainerHeight}"
      :theme="theme"
    />
  </el-container>
</template>

<style scoped>

</style>
