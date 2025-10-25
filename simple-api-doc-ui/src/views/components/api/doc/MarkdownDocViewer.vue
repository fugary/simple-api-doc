<script setup>
import { computed, watch, ref } from 'vue'
import { MdCatalog, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import ApiDocViewHeader from '@/views/components/api/doc/comp/ApiDocViewHeader.vue'
import { useCopyRight, useContainerCheck } from '@/services/api/ApiCommonService'
import ApiDocApi from '@/api/ApiDocApi'
import { loadMdDoc } from '@/api/SimpleShareApi'
import { $coreHideLoading, $coreShowLoading } from '@/utils'
import { useInitLoadOnce } from '@/hooks/CommonHooks'

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
  $coreShowLoading({ delay: 0, target: '.home-main' })
  if (!props.shareDoc) {
    return ApiDocApi.getById(id).then(data => {
      Object.assign(currentDoc.value, data.resultData)
      historyCount.value = data.addons?.historyCount || 0
      $coreHideLoading()
    }).catch(() => $coreHideLoading())
  } else {
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
const copyRight = useCopyRight(props.shareDoc)

const { isSmallContainer, containerRef } = useContainerCheck()
defineEmits(['updateHistory'])
</script>

<template>
  <el-container
    :key="`markdown-doc-preview-${currentDoc.id}`"
    class="padding-left2 padding-right2 flex-column"
    :style="!isSmallContainer?'height:100%':''"
  >
    <api-doc-view-header
      v-model="currentDoc"
      :history-count="historyCount"
      :editable="editable"
      @update-history="$emit('updateHistory', $event)"
    />
    <el-container
      ref="containerRef"
      class="markdown-doc-viewer scroll-main-container"
    >
      <md-preview
        class="md-doc-container"
        :editor-id="id"
        :theme="theme"
        :model-value="currentDoc.docContent"
      />
      <el-scrollbar
        v-if="!isSmallContainer"
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
    <el-container
      class="text-center padding-10 padding-bottom3 flex-center"
      style="flex-grow: 0"
    >
      <span>
        <el-text><copy-right /></el-text>
      </span>
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

</style>
