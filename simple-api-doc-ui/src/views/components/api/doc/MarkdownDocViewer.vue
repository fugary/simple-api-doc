<script setup>
import { computed } from 'vue'
import { MdCatalog, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import ApiDocViewHeader from '@/views/components/api/doc/comp/ApiDocViewHeader.vue'
import { useFolderLayoutHeight } from '@/services/api/ApiFolderService'
import { useScreenCheck } from '@/services/api/ApiCommonService'

const { isMobile } = useScreenCheck()
const docMargin = computed(() => isMobile.value ? 0 : '220px')

const props = defineProps({
  scrollElement: {
    type: [Object, String],
    default: document.documentElement
  },
  scrollElementOffsetTop: {
    type: Number,
    default: 0
  },
  editable: {
    type: Boolean,
    default: false
  }
})
const id = 'markdown-doc-preview-only'
const currentDoc = defineModel({
  type: Object,
  default: undefined
})

const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')
const folderContainerHeight = useFolderLayoutHeight(props.editable, 20)

</script>

<template>
  <el-container class="padding-left2 padding-right2 flex-column">
    <api-doc-view-header
      v-model="currentDoc"
      :editable="editable"
    />
    <el-container
      class="markdown-doc-viewer"
      :style="{height: folderContainerHeight}"
    >
      <md-preview
        class="md-doc-container"
        :editor-id="id"
        :theme="theme"
        :model-value="currentDoc.docContent"
      />
      <md-catalog
        v-if="!isMobile"
        class="md-catalog"
        :editor-id="id"
        :scroll-element="scrollElement"
        :scroll-element-offset-top="scrollElementOffsetTop"
      />
    </el-container>
    <el-backtop
      v-common-tooltip="$t('common.label.backtop')"
      target=".md-editor-preview-wrapper"
      :right="70"
      :bottom="70"
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
  margin-right: v-bind(docMargin);
}
</style>
