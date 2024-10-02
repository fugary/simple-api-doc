<script setup>
import { computed } from 'vue'
import { MdCatalog, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import ApiDocViewHeader from '@/views/components/api/doc/comp/ApiDocViewHeader.vue'

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
const currentDoc = defineModel({
  type: Object,
  default: undefined
})

const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')

</script>

<template>
  <el-container class="padding-left2 padding-right2 flex-column">
    <api-doc-view-header
      v-model="currentDoc"
    />
    <el-container>
      <md-preview
        class="md-doc-container"
        :editor-id="id"
        :theme="theme"
        :model-value="currentDoc.docContent"
      />
      <md-catalog
        class="md-catalog"
        :editor-id="id"
        :scroll-element="scrollElement"
        :scroll-element-offset-top="scrollElementOffsetTop"
      />
    </el-container>
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
