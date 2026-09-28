<script setup>
import { ref } from 'vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import CommonIcon from '@/components/common-icon/index.vue'
import { $coreConfirm } from '@/utils'
import { $i18nBundle } from '@/messages'
import { isMarkdownDoc } from '@/services/api/ApiProjectService'

defineProps({
  recentDocs: {
    type: Array,
    default: () => []
  },
  currentDoc: {
    type: Object,
    default: null
  },
  icon: {
    type: String,
    default: 'HistoryFilled'
  },
  iconSize: {
    type: Number,
    default: 20
  }
})

const emit = defineEmits(['selectDoc', 'removeDoc', 'clearDocs'])
const popoverRef = ref()

const handleSelect = (item) => {
  emit('selectDoc', item)
  popoverRef.value?.hide?.()
}

const handleRemove = (item) => {
  emit('removeDoc', item)
}

const handleClear = () => {
  $coreConfirm($i18nBundle('api.msg.clearRecentConfirm')).then(() => {
    emit('clearDocs')
    popoverRef.value?.hide?.()
  }).catch(() => {})
}
</script>

<template>
  <span class="api-recent-popover-wrapper">
    <el-popover
      ref="popoverRef"
      placement="bottom-end"
      :width="340"
      trigger="hover"
      transition="el-zoom-in-top"
      :show-after="100"
      :hide-after="200"
      popper-class="api-recent-popover-popper"
    >
      <template #reference>
        <el-link
          type="info"
          underline="never"
          class="recent-history-link"
        >
          <common-icon
            :size="iconSize"
            :icon="icon"
          />
        </el-link>
      </template>
      <div class="recent-popover-content">
        <div class="recent-popover-header">
          <span class="recent-header-title">
            <common-icon
              :icon="icon"
              class="margin-right1"
            />
            {{ $t('api.label.recentDocs') }} ({{ recentDocs.length }})
          </span>
          <el-button
            type="danger"
            link
            size="small"
            class="clear-history-btn"
            @click="handleClear"
          >
            <common-icon
              icon="Delete"
              class="margin-right1"
            />
            {{ $t('common.label.clear') }}
          </el-button>
        </div>
        <el-scrollbar max-height="340px">
          <div class="recent-list">
            <div
              v-for="item in recentDocs"
              :key="item.id"
              class="recent-item"
              :class="{ 'is-active': item.id === currentDoc?.id }"
              @click="handleSelect(item)"
            >
              <div class="recent-item-main">
                <div class="recent-item-row1">
                  <common-icon
                    :icon="isMarkdownDoc(item) ? 'custom-markdown' : 'custom-api'"
                    class="tree-label-icon"
                    :class="isMarkdownDoc(item) ? 'md-icon' : 'api-icon'"
                    :size="18"
                  />
                  <api-method-tag
                    v-if="!isMarkdownDoc(item)"
                    :method="item.method"
                  />
                  <del
                    v-if="item.deprecated"
                    class="recent-doc-name"
                    :title="item.docName"
                  >
                    {{ item.docName }}
                  </del>
                  <span
                    v-else
                    class="recent-doc-name"
                    :title="item.docName"
                  >
                    {{ item.docName }}
                  </span>
                  <el-tag
                    v-if="item.id === currentDoc?.id"
                    size="small"
                    type="primary"
                    effect="plain"
                    class="recent-current-tag"
                  >
                    {{ $t('api.label.current') }}
                  </el-tag>
                </div>
                <div
                  v-if="item.url"
                  class="recent-doc-url"
                  :title="item.url"
                >
                  {{ item.url }}
                </div>
              </div>
              <div
                class="recent-item-delete"
                :title="$t('common.label.delete')"
                @click.stop="handleRemove(item)"
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
    </el-popover>
  </span>
</template>

<style scoped>
.api-recent-popover-wrapper {
  display: inline-flex;
  align-items: center;
}

.recent-popover-content {
  margin: -4px -6px -2px -6px;
}

.recent-popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 6px 8px 6px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.recent-header-title {
  font-weight: 600;
  font-size: 13px;
  display: inline-flex;
  align-items: center;
}

.clear-history-btn {
  font-size: 12px;
  padding: 0 4px;
}

.recent-list {
  padding: 4px 0;
}

.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.recent-item:hover {
  background-color: var(--el-fill-color-light);
}

.recent-item.is-active {
  background-color: var(--el-color-primary-light-9);
}

.recent-item-main {
  flex: 1;
  min-width: 0;
  margin-right: 6px;
}

.recent-item-row1 {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tree-label-icon {
  vertical-align: middle;
  flex-shrink: 0;
}

.tree-label-icon.md-icon {
  color: #8b5cf6;
}

.tree-label-icon.api-icon {
  color: #10b981;
}

.recent-doc-name {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.recent-current-tag {
  font-size: 10px;
  height: 18px;
  line-height: 16px;
  padding: 0 4px;
  flex-shrink: 0;
}

.recent-doc-url {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2px;
}

.recent-item-delete {
  opacity: 0;
  transition: opacity 0.2s;
  color: var(--el-text-color-secondary);
  padding: 3px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.recent-item:hover .recent-item-delete {
  opacity: 1;
}

.recent-item-delete:hover {
  color: var(--el-color-danger);
  background-color: var(--el-fill-color);
}
</style>
