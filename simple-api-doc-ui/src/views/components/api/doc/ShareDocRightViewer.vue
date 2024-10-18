<script setup>
import { APP_VERSION } from '@/config'
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

</script>

<template>
  <div>
    <markdown-doc-viewer
      v-if="currentDoc?.docType==='md'"
      v-model="currentDoc"
      scroll-element=".markdown-doc-viewer .md-editor-preview-wrapper"
    />
    <api-doc-viewer
      v-if="currentDoc?.docType==='api'"
      v-model="currentDoc"
      :share-doc="projectShare"
      :project-item="projectItem"
    />
    <el-container class="text-center padding-10 flex-center">
      <span>
        <el-text>Copyright © 2024 {{ projectShare?.copyRight||'' }} Version: {{ APP_VERSION }}</el-text>
      </span>
    </el-container>
  </div>
</template>

<style scoped>

</style>
