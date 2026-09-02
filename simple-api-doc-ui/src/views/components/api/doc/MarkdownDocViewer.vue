<script setup>
import { computed, watch, ref, reactive, nextTick } from 'vue'
import { MdPreview } from 'md-editor-v3'
import MarkdownDocCatalog from '@/views/components/api/doc/comp/MarkdownDocCatalog.vue'
import 'md-editor-v3/lib/preview.css'
import ApiDocViewHeader from '@/views/components/api/doc/comp/ApiDocViewHeader.vue'
import { useCopyRight, useContainerCheck } from '@/services/api/ApiCommonService'
import ApiDocApi from '@/api/ApiDocApi'
import { loadMdDoc } from '@/api/SimpleShareApi'
import { $copyText, $coreHideLoading, $coreShowLoading } from '@/utils'
import { useInitLoadOnce } from '@/hooks/CommonHooks'
import { calcPreferenceId, calcSharePreference, useShareDocTheme } from '@/services/api/ApiFolderService'
import { resolveRelativeDocPath, findMatchingDoc, getDocAncestorTreeIds, scrollToAnchor, setPendingAnchor, consumePendingAnchor } from '@/services/api/MarkdownLinkService'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import { $i18nBundle } from '@/messages'
import { ElMessage } from 'element-plus'
import emitter from '@/vendors/emitter'

const shareConfigStore = useShareConfigStore()

const props = defineProps({
  scrollElement: {
    type: [Object, String],
    default: undefined
  },
  scrollElementOffsetTop: {
    type: Number,
    default: 0
  },
  editable: {
    type: Boolean,
    default: false
  },
  projectItem: {
    type: Object,
    default: () => ({})
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

const finalizeLoad = () => {
  $coreHideLoading()
  const anchor = consumePendingAnchor()
  if (anchor) {
    nextTick(() => {
      setTimeout(() => {
        scrollToAnchor(anchor, {
          scrollElement: props.scrollElement,
          offsetTop: props.scrollElementOffsetTop
        })
      }, 150)
    })
  } else {
    nextTick(() => {
      const scrollEl = typeof props.scrollElement === 'string'
        ? document.querySelector(props.scrollElement)
        : props.scrollElement
      if (scrollEl) {
        scrollEl.scrollTop = 0
      }
    })
  }
}

const loadCurrentDoc = (id) => {
  currentDoc.value && (currentDoc.value.docContent = '')
  $coreShowLoading({ delay: 0, target: '.home-main' })
  if (!props.shareDoc) {
    return ApiDocApi.getById(id).then(data => {
      Object.assign(currentDoc.value, data.resultData)
      historyCount.value = data.addons?.historyCount || 0
      finalizeLoad()
    }).catch(() => $coreHideLoading())
  } else {
    return loadMdDoc({
      shareId: props.shareDoc.shareId,
      docId: id
    }, { showErrorMessage: false }).then(data => {
      Object.assign(currentDoc.value, data.resultData)
      finalizeLoad()
    }).catch(err => {
      emitter.emit('share-doc-error', err)
      $coreHideLoading()
    })
  }
}

const { initLoadOnce } = useInitLoadOnce(() => loadCurrentDoc(currentDoc.value.id))

watch(currentDoc, (newDoc, oldDoc) => {
  if (newDoc.id && (newDoc.id !== oldDoc?.id || newDoc.docContent !== oldDoc?.docContent || newDoc.docName !== oldDoc?.docName)) {
    initLoadOnce()
  }
}, { immediate: true })

const preferenceId = computed(() => calcPreferenceId(props.projectItem, props.shareDoc))
watch(preferenceId, (prefId) => {
  if (prefId && !shareConfigStore.sharePreferenceView[prefId]) {
    shareConfigStore.sharePreferenceView[prefId] = reactive({})
  }
}, { immediate: true })
const sharePreference = computed(() => {
  const prefId = preferenceId.value
  return (prefId && shareConfigStore.sharePreferenceView[prefId]) || {}
})
const { isDarkTheme } = useShareDocTheme(calcSharePreference(props.projectItem, props.shareDoc))
const theme = computed(() => isDarkTheme.value ? 'dark' : 'light')
const copyRight = useCopyRight(props.shareDoc)

const { isSmallContainer, containerRef } = useContainerCheck()
defineEmits(['updateHistory'])

const handleCopyMarkdown = () => {
  const name = currentDoc.value?.docName
  const markdown = currentDoc.value?.docContent || ''
  const content = name ? `# ${name}\n\n${markdown}` : markdown
  $copyText(content)
}

const handleContainerClick = (event) => {
  const linkEl = event.target.closest('a')
  if (!linkEl) return

  const href = linkEl.getAttribute('href')
  if (!href) return

  const { targetPath, hash, isExternal, isAnchorOnly } = resolveRelativeDocPath(currentDoc.value, href, props.projectItem?.folders)

  // 1. 外部链接：保持新标签页打开
  if (isExternal) {
    linkEl.setAttribute('target', '_blank')
    linkEl.setAttribute('rel', 'noopener noreferrer')
    return
  }

  event.preventDefault()

  // 2. 纯本页锚点 (#section)
  if (isAnchorOnly) {
    if (hash) {
      scrollToAnchor(hash, {
        scrollElement: props.scrollElement,
        offsetTop: props.scrollElementOffsetTop
      })
    }
    return
  }

  // 3. 相对路径跨文档链接
  const targetDoc = findMatchingDoc(props.projectItem?.docs, targetPath, props.projectItem?.folders)
  if (targetDoc) {
    // 3.1 目标为当前文档自身
    if (targetDoc.id === currentDoc.value?.id) {
      if (hash) {
        scrollToAnchor(hash, {
          scrollElement: props.scrollElement,
          offsetTop: props.scrollElementOffsetTop
        })
      }
      return
    }

    // 3.2 跨文档跳转：记录待定位锚点（全局安全）
    setPendingAnchor(hash || null)

    // 3.3 自动展开左侧目录树的父级文件夹并更新 preference
    const preferenceId = calcPreferenceId(props.projectItem, props.shareDoc)
    const sharePreference = shareConfigStore.sharePreferenceView[preferenceId]
    if (sharePreference) {
      const ancestorKeys = getDocAncestorTreeIds(targetDoc, props.projectItem?.folders)
      const expandedSet = new Set(sharePreference.lastExpandKeys || [])
      ancestorKeys.forEach(k => expandedSet.add(k))
      sharePreference.lastExpandKeys = Array.from(expandedSet)
      sharePreference.lastDocId = targetDoc.id
    }

    // 3.4 派发事件由左侧树统一驱动文档加载与高亮（避免当前组件销毁导致状态丢失）
    emitter.emit('select-api-doc', targetDoc)
  } else {
    ElMessage.warning($i18nBundle('api.msg.docNotFoundDetail', [targetPath || href]))
  }
}
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
      @click="handleContainerClick"
    >
      <md-preview
        class="md-doc-container"
        :editor-id="id"
        :theme="theme"
        :model-value="currentDoc.docContent"
      />
      <markdown-doc-catalog
        v-if="!isSmallContainer"
        v-model:collapsed="sharePreference.hideCatalog"
        :editor-id="id"
        :theme="theme"
        :scroll-element="scrollElement"
        :scroll-element-offset-top="scrollElementOffsetTop"
      />
    </el-container>
    <el-container
      class="text-center padding-10 padding-bottom3 flex-center"
      style="flex-grow: 0"
    >
      <span>
        <el-text><copy-right /></el-text>
      </span>
    </el-container>
    <template v-if="currentDoc">
      <div
        v-common-tooltip="$t('api.label.copyMarkdown')"
        class="floating-action-btn copy-md-btn"
        :style="shareDoc ? { bottom: '90px' } : {}"
        @click="handleCopyMarkdown"
      >
        <common-icon
          icon="CopyDocument"
          :size="16"
        />
      </div>
      <el-backtop
        v-common-tooltip="$t('common.label.backtop')"
        target=".md-editor-preview-wrapper"
        :right="40"
        :bottom="40"
      />
    </template>
  </el-container>
</template>

<style scoped>

</style>
