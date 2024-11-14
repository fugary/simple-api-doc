<script setup>
import { computed, watch, ref } from 'vue'
import { MdCatalog, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import ApiDocViewHeader from '@/views/components/api/doc/comp/ApiDocViewHeader.vue'
import { useScreenCheck } from '@/services/api/ApiCommonService'
import ApiDocApi from '@/api/ApiDocApi'
import { loadMdDoc } from '@/api/SimpleShareApi'
import { $coreHideLoading, $coreShowLoading } from '@/utils'
import { useInitLoadOnce } from '@/hooks/CommonHooks'

const { isMobile } = useScreenCheck()

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
  },
  shareDoc: {
    type: Object,
    default: undefined
  }
})
const id = 'markdown-doc-preview-only'
const currentDoc = defineModel({
  type: Object,
  default: undefined
})
const historyCount = ref(0)

const loadCurrentDoc = (id) => {
  currentDoc.value && (currentDoc.value.docContent = '')
  $coreShowLoading({ delay: 0 })
  if (props.editable) {
    return ApiDocApi.getById(id).then(data => {
      Object.assign(currentDoc.value, data.resultData)
      historyCount.value = data.addons?.historyCount || 0
      $coreHideLoading()
    }).catch(() => $coreHideLoading())
  } else if (props.shareDoc) {
    return loadMdDoc({
      shareId: props.shareDoc.shareId,
      docId: id
    }).then(data => {
      Object.assign(currentDoc.value, data.resultData)
      $coreHideLoading()
    }).catch(() => $coreHideLoading())
  }
}

const { initLoadOnce } = useInitLoadOnce(() => loadCurrentDoc(currentDoc.value.id))

watch(currentDoc, (newDoc, oldDoc) => {
  if (newDoc.id && (newDoc.id !== oldDoc?.id || newDoc.docContent !== oldDoc?.docContent || newDoc.docName !== oldDoc?.docName)) {
    initLoadOnce()
  }
}, { immediate: true })

const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')

</script>

<template>
  <el-container
    :key="`markdown-doc-preview-${currentDoc.id}`"
    class="padding-left2 padding-right2 flex-column"
    :style="!isMobile?'height: calc(100% - 45px)':''"
  >
    <api-doc-view-header
      v-model="currentDoc"
      :editable="editable"
      :history-count="historyCount"
    />
    <el-container
      class="markdown-doc-viewer scroll-main-container"
    >
      <md-preview
        class="md-doc-container"
        :editor-id="id"
        :theme="theme"
        no-mermaid
        no-katex
        :model-value="currentDoc.docContent"
      />
      <el-scrollbar
        v-if="!isMobile"
        class="md-doc-catalog"
      >
        <md-catalog
          class="md-catalog"
          :editor-id="id"
          :scroll-element="scrollElement"
          :scroll-element-offset-top="scrollElementOffsetTop"
        />
      </el-scrollbar>
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
.md-doc-container {
  flex-grow: 1;
}
.md-doc-catalog {
  overflow: unset;
  overflow-x: visible;
}
</style>
