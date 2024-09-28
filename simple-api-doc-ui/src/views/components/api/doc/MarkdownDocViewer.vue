<script setup>
import { computed } from 'vue'
import { MdCatalog, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { calcAffixOffset } from '@/utils'

const id = 'markdown-doc-preview-only'
const vModel = defineModel({
  type: String,
  default: ''
})

const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')
const scrollElement = '.home-main'

</script>

<template>
  <el-container class="padding-left2 padding-right2">
    <md-preview
      :editor-id="id"
      :theme="theme"
      :model-value="vModel"
    />
    <md-catalog
      class="md-catalog"
      :editor-id="id"
      :scroll-element="scrollElement"
      :scroll-element-offset-top="calcAffixOffset()"
    />
  </el-container>
</template>

<style scoped>
.md-catalog {
  position: fixed;
  right: 40px;
}
</style>
