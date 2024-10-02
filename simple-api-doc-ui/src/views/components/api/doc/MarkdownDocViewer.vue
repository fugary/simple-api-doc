<script setup>
import { computed } from 'vue'
import { MdCatalog, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'

defineProps({
  scrollElement: {
    type: [Object, String],
    default: document.documentElement
  },
  scrollElementOffsetTop: {
    type: Number,
    default: 0
  }
})
const id = 'markdown-doc-preview-only'
const vModel = defineModel({
  type: String,
  default: ''
})

const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')

</script>

<template>
  <el-container class="padding-left2 padding-right2">
    <md-preview
      class="md-doc-container"
      :editor-id="id"
      :theme="theme"
      :model-value="vModel"
    />
    <md-catalog
      class="md-catalog"
      :editor-id="id"
      :scroll-element="scrollElement"
      :scroll-element-offset-top="scrollElementOffsetTop"
    />
  </el-container>
</template>

<style scoped>
.md-catalog {
  position: fixed;
  right: 40px;
  width: 200px;
}
.md-doc-container {
  margin-right: 220px;
}
</style>
