<script setup>
import { ref } from 'vue'
import { MdCatalog } from 'md-editor-v3'
import CommonIcon from '@/components/common-icon/index.vue'

defineProps({
  editorId: {
    type: String,
    required: true
  },
  scrollElement: {
    type: [Object, String],
    default: undefined
  },
  scrollElementOffsetTop: {
    type: Number,
    default: 0
  },
  theme: {
    type: String,
    default: undefined
  }
})

const isCollapsed = defineModel('collapsed', {
  type: Boolean,
  default: false
})

const scrollbarRef = ref()

const handleActive = (tocItem, ele) => {
  if (isCollapsed.value || !ele || !scrollbarRef.value?.wrapRef) return
  const wrap = scrollbarRef.value.wrapRef
  const wrapRect = wrap.getBoundingClientRect()
  const eleRect = ele.getBoundingClientRect()

  if (eleRect.top < wrapRect.top + 20) {
    const diff = eleRect.top - wrapRect.top - 20
    scrollbarRef.value.setScrollTop(Math.max(0, wrap.scrollTop + diff))
  } else if (eleRect.bottom > wrapRect.bottom - 20) {
    const diff = eleRect.bottom - wrapRect.bottom + 20
    scrollbarRef.value.setScrollTop(wrap.scrollTop + diff)
  }
}

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}
</script>

<template>
  <div
    class="md-doc-catalog-wrapper"
    :class="{ 'is-collapsed': isCollapsed }"
  >
    <!-- 侧边贴边折叠/展开触发把手 (Trigger Handle) -->
    <div
      class="catalog-collapse-trigger"
      :class="{ 'trigger-collapsed': isCollapsed }"
      @click="toggleCollapse"
    >
      <common-icon
        :icon="isCollapsed ? 'DArrowLeft' : 'DArrowRight'"
        :size="12"
      />
      <span
        v-if="isCollapsed"
        class="trigger-text"
      >
        {{ $t('api.label.catalog') }}
      </span>
    </div>

    <!-- 目录展开时的主体内容 -->
    <div
      v-show="!isCollapsed"
      class="catalog-main"
    >
      <div class="catalog-header">
        <span class="catalog-title">
          <common-icon
            icon="List"
            :size="14"
            class="margin-right1"
          />
          {{ $t('api.label.catalog') }}
        </span>
        <div
          class="catalog-close-btn"
          @click="toggleCollapse"
        >
          <common-icon
            icon="DArrowRight"
            :size="13"
          />
        </div>
      </div>
      <el-scrollbar
        ref="scrollbarRef"
        class="md-doc-catalog-scrollbar"
      >
        <md-catalog
          class="md-catalog"
          :editor-id="editorId"
          :theme="theme"
          :scroll-element="scrollElement"
          :scroll-element-offset-top="scrollElementOffsetTop"
          :on-active="handleActive"
        />
      </el-scrollbar>
    </div>
  </div>
</template>

<style scoped>
.md-doc-catalog-wrapper {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 260px;
  min-width: 200px;
  max-width: 30%;
  flex-shrink: 0;
  border-left: 1px solid var(--el-border-color-lighter);
  background-color: var(--el-bg-color);
  box-sizing: border-box;
  transition: width 0.2s ease, min-width 0.2s ease, max-width 0.2s ease, border-color 0.2s ease;
}

.md-doc-catalog-wrapper.is-collapsed {
  width: 0 !important;
  min-width: 0 !important;
  max-width: 0 !important;
  border-left-color: transparent !important;
  padding: 0 !important;
  overflow: visible;
}

.catalog-collapse-trigger {
  position: absolute;
  top: 30px;
  left: -14px;
  z-index: 10;
  width: 14px;
  padding: 8px 0;
  border-radius: 6px 0 0 6px;
  background-color: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-right: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  box-shadow: -2px 0 6px rgba(0, 0, 0, 0.04);
  color: var(--el-text-color-secondary);
  transition: all 0.2s ease;
  user-select: none;
}

.catalog-collapse-trigger:hover {
  color: var(--el-color-primary);
  background-color: var(--el-fill-color-light);
}

.catalog-collapse-trigger:not(.trigger-collapsed):hover {
  width: 16px;
  left: -16px;
}

.catalog-collapse-trigger.trigger-collapsed {
  left: auto;
  right: 0;
  width: auto;
  min-width: 22px;
  padding: 8px 4px;
  box-shadow: -2px 2px 8px rgba(0, 0, 0, 0.08);
  gap: 4px;
  color: var(--el-text-color-regular);
}

.trigger-text {
  writing-mode: vertical-rl;
  font-size: 12px;
  line-height: 1.2;
  letter-spacing: 2px;
}

.catalog-main {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  min-width: 200px;
  overflow: hidden;
}

.catalog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px 6px 14px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  border-bottom: 1px solid var(--el-border-color-extra-light);
  user-select: none;
}

.catalog-title {
  display: flex;
  align-items: center;
}

.catalog-close-btn {
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.catalog-close-btn:hover {
  color: var(--el-color-primary);
  background-color: var(--el-fill-color-light);
}

.md-doc-catalog-scrollbar {
  flex: 1;
  overflow: hidden;
}

.md-doc-catalog-scrollbar :deep(.md-catalog) {
  padding: 4px 6px;
}

.md-doc-catalog-scrollbar :deep(.md-editor-catalog-link span) {
  word-break: break-all;
  white-space: normal;
  text-overflow: unset;
}
</style>
