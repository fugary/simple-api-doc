<script setup>
import MarkdownDocViewer from '@/views/components/api/doc/MarkdownDocViewer.vue'
import ApiDocViewer from '@/views/components/api/doc/ApiDocViewer.vue'

defineProps({
  projectShare: {
    type: Object,
    default: undefined
  },
  projectItem: {
    type: Object,
    default: undefined
  }
})

const currentDoc = defineModel({
  type: Object,
  default: undefined
})

defineEmits(['toDebugApi'])

</script>

<template>
  <div class="height100">
    <markdown-doc-viewer
      v-if="currentDoc?.docType==='md'"
      v-model="currentDoc"
      :share-doc="projectShare"
      :project-item="projectItem"
      scroll-element=".markdown-doc-viewer .md-editor-preview-wrapper"
    />
    <api-doc-viewer
      v-if="currentDoc?.docType==='api'"
      v-model="currentDoc"
      :share-doc="projectShare"
      :project-item="projectItem"
      @to-debug-api="(...args)=>$emit('toDebugApi', ...args)"
    />
  </div>
</template>

<style scoped>

</style>
