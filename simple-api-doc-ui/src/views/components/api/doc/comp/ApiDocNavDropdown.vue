<script setup>
import { ref, inject, onUnmounted } from 'vue'
import { isMarkdownDoc } from '@/services/api/ApiProjectService'
import { $coreConfirm } from '@/utils'
import { $i18nBundle } from '@/messages'
import emitter from '@/vendors/emitter'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import CommonIcon from '@/components/common-icon/index.vue'

const props = defineProps({
  direction: {
    type: String,
    required: true,
    validator: v => ['back', 'forward'].includes(v)
  },
  preferenceId: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const shareConfigStore = useShareConfigStore()
const projectItem = inject('projectItem', null)

const dropdownRef = ref()
const historyList = ref([])
let longPressTimer = null
let isLongPressTriggered = false

const loadHistoryList = () => {
  const ids = props.direction === 'back'
    ? shareConfigStore.getBackHistory(props.preferenceId)
    : shareConfigStore.getForwardHistory(props.preferenceId)

  if (!ids?.length) {
    historyList.value = []
    return
  }

  const docs = projectItem?.value?.docs || []
  const docMap = new Map(docs.map(d => [d.id, d]))

  historyList.value = ids.map(({ id, index }) => {
    const doc = docMap.get(id)
    if (doc) {
      return {
        id,
        index,
        docName: doc.docName || doc.label,
        method: doc.method,
        url: doc.url,
        docType: isMarkdownDoc(doc) ? 'md' : 'api',
        deprecated: !!doc.deprecated
      }
    }
    return {
      id,
      index,
      docName: `#${id}`,
      docType: 'api'
    }
  })
}

const handleVisibleChange = (visible) => {
  if (visible) {
    loadHistoryList()
  }
}

const handleMouseDown = (e) => {
  if (props.disabled || (e && e.button !== undefined && e.button !== 0)) {
    return
  }
  isLongPressTriggered = false
  clearTimeout(longPressTimer)
  longPressTimer = setTimeout(() => {
    isLongPressTriggered = true
    loadHistoryList()
    dropdownRef.value?.handleOpen?.()
  }, 350)
}

const handleMouseUp = () => {
  clearTimeout(longPressTimer)
}

onUnmounted(() => {
  clearTimeout(longPressTimer)
})

const handleBtnClick = () => {
  if (isLongPressTriggered) {
    isLongPressTriggered = false
    return
  }
  const targetDocId = props.direction === 'back'
    ? shareConfigStore.navBack(props.preferenceId)
    : shareConfigStore.navForward(props.preferenceId)

  if (targetDocId) {
    emitter.emit('select-api-doc', { id: targetDocId })
  }
}

const handleSelectHistory = (targetIndex) => {
  const targetDocId = shareConfigStore.navToIndex(props.preferenceId, targetIndex)
  if (targetDocId) {
    emitter.emit('select-api-doc', { id: targetDocId })
  }
  dropdownRef.value?.handleClose?.()
}

const handleRemoveHistory = (targetIndex) => {
  shareConfigStore.removeNavIndex(props.preferenceId, targetIndex)
  loadHistoryList()
  if (!historyList.value.length) {
    dropdownRef.value?.handleClose?.()
  }
}

const handleClearHistory = () => {
  $coreConfirm($i18nBundle('api.msg.clearNavHistoryConfirm')).then(() => {
    shareConfigStore.clearNavStack(props.preferenceId)
    historyList.value = []
    dropdownRef.value?.handleClose?.()
  }).catch(() => {})
}
</script>

<template>
  <el-dropdown
    ref="dropdownRef"
    trigger="contextmenu"
    :disabled="disabled"
    popper-class="doc-nav-history-popper"
    @visible-change="handleVisibleChange"
  >
    <el-button
      link
      size="small"
      class="doc-nav-btn"
      :disabled="disabled"
      :title="direction === 'back' ? $t('api.label.navBackTip') : $t('api.label.navForwardTip')"
      @click="handleBtnClick"
      @mousedown="handleMouseDown"
      @mouseup="handleMouseUp"
      @mouseleave="handleMouseUp"
      @touchstart.passive="handleMouseDown"
      @touchend="handleMouseUp"
    >
      <common-icon
        :icon="direction === 'back' ? 'ArrowBackFilled' : 'ArrowForwardFilled'"
        :size="18"
      />
    </el-button>
    <template #dropdown>
      <div class="doc-nav-popover-content">
        <div class="doc-nav-popover-header">
          <span class="doc-nav-header-title">
            <common-icon
              :icon="direction === 'back' ? 'ArrowBackFilled' : 'ArrowForwardFilled'"
              class="margin-right1"
            />
            {{ direction === 'back' ? $t('api.label.navBackHistory') : $t('api.label.navForwardHistory') }} ({{ historyList.length }})
          </span>
          <el-button
            type="danger"
            link
            size="small"
            class="clear-history-btn"
            @click="handleClearHistory"
          >
            <common-icon
              icon="Delete"
              class="margin-right1"
            />
            {{ $t('common.label.clear') }}
          </el-button>
        </div>
        <el-scrollbar max-height="340px">
          <div class="doc-nav-list">
            <div
              v-for="entry in historyList"
              :key="entry.index"
              class="doc-nav-item"
              :class="{ 'has-url': !!entry.url }"
              @click="handleSelectHistory(entry.index)"
            >
              <common-icon
                :icon="entry.docType === 'md' ? 'custom-markdown' : 'custom-api'"
                class="tree-label-icon"
                :class="entry.docType === 'md' ? 'md-icon' : 'api-icon'"
                :size="18"
              />
              <api-method-tag
                v-if="entry.docType !== 'md' && entry.method"
                :method="entry.method"
              />
              <del
                v-if="entry.deprecated"
                class="doc-nav-doc-name"
                :title="entry.docName"
              >
                {{ entry.docName }}
              </del>
              <span
                v-else
                class="doc-nav-doc-name"
                :title="entry.docName"
              >
                {{ entry.docName }}
              </span>
              <span
                v-if="entry.url"
                class="doc-nav-doc-url"
                :title="entry.url"
              >
                {{ entry.url }}
              </span>
              <span
                v-else
                class="doc-nav-doc-spacer"
              />
              <div
                class="doc-nav-item-delete"
                :title="$t('common.label.delete')"
                @click.stop="handleRemoveHistory(entry.index)"
              >
                <common-icon
                  icon="Close"
                  :size="14"
                />
              </div>
            </div>
          </div>
        </el-scrollbar>
      </div>
    </template>
  </el-dropdown>
</template>

<style scoped>
.doc-nav-btn {
  padding: 4px;
  height: 26px;
  width: 26px;
  border-radius: 4px;
  color: var(--el-text-color-regular);
}

.doc-nav-btn:hover:not(:disabled) {
  background-color: var(--el-fill-color-light);
  color: var(--el-color-primary);
}

.doc-nav-btn.is-disabled {
  color: var(--el-text-color-placeholder);
  cursor: not-allowed;
  opacity: 0.4;
}
</style>

<style>
.doc-nav-history-popper,
.doc-nav-history-popper.el-popper {
  --el-dropdown-menuItem-hover-fill: transparent;
  padding: 0 !important;
}

.doc-nav-history-popper .el-dropdown__popper {
  padding: 0 !important;
}

.doc-nav-popover-content {
  width: 460px;
  max-width: 90vw;
  box-sizing: border-box;
}

.doc-nav-popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.doc-nav-header-title {
  font-weight: 600;
  font-size: 13px;
  display: inline-flex;
  align-items: center;
}

.clear-history-btn {
  font-size: 12px;
  padding: 0 4px;
}

.doc-nav-list {
  padding: 4px;
}

.doc-nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  height: 34px;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.2s;
  box-sizing: border-box;
}

.doc-nav-item:hover {
  background-color: var(--el-fill-color-light);
}

.doc-nav-item .tree-label-icon {
  vertical-align: middle;
  flex-shrink: 0;
}

.doc-nav-item .tree-label-icon.md-icon {
  color: #8b5cf6;
}

.doc-nav-item .tree-label-icon.api-icon {
  color: #10b981;
}

.doc-nav-doc-name {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 1;
  max-width: calc(100% - 60px);
}

.doc-nav-item.has-url .doc-nav-doc-name {
  max-width: 48%;
}

.doc-nav-doc-url {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.doc-nav-doc-spacer {
  flex: 1;
}

.doc-nav-item-delete {
  opacity: 0;
  transition: opacity 0.2s;
  color: var(--el-text-color-secondary);
  width: 20px;
  height: 20px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.doc-nav-item:hover .doc-nav-item-delete {
  opacity: 1;
}

.doc-nav-item-delete:hover {
  color: var(--el-color-danger);
  background-color: var(--el-fill-color);
}
</style>
