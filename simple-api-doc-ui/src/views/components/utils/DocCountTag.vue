<script setup>
import { computed } from 'vue'
import { $i18nBundle } from '@/messages'
import CommonIcon from '@/components/common-icon/index.vue'

const props = defineProps({
  stats: {
    type: Object,
    default: () => ({})
  },
  iconSize: {
    type: [Number, String],
    default: 14
  },
  pointer: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['click'])

// 判断是否为"已选/总计"模式（stats 中包含 apiSelected/mdSelected/totalSelected）
const isSelectedMode = computed(() => {
  const s = props.stats
  return s.apiSelected !== undefined || s.mdSelected !== undefined || s.totalSelected !== undefined
})

// === 接口 ===
const apiTotal = computed(() => props.stats.totalApi ?? props.stats.apiCount ?? 0)
const apiSelected = computed(() => props.stats.apiSelected ?? 0)
const showApi = computed(() => apiTotal.value > 0)
const apiText = computed(() => isSelectedMode.value ? `${apiSelected.value}/${apiTotal.value}` : String(apiTotal.value))

// === 文档 ===
const docTotal = computed(() => props.stats.totalMd ?? props.stats.docCount ?? 0)
const docSelected = computed(() => props.stats.mdSelected ?? 0)
const showDoc = computed(() => docTotal.value > 0)
const docText = computed(() => isSelectedMode.value ? `${docSelected.value}/${docTotal.value}` : String(docTotal.value))

// === 总计 ===
const totalAll = computed(() => props.stats.totalLeaf ?? props.stats.totalCount ?? (apiTotal.value + docTotal.value))
const totalSelected = computed(() => props.stats.totalSelected ?? 0)
const totalText = computed(() => isSelectedMode.value ? `${totalSelected.value}/${totalAll.value}` : String(totalAll.value))

const showTotal = computed(() => {
  // 同时有接口和文档时显示汇总，两者都没有时也显示（展示 0）
  if (showApi.value && showDoc.value) return true
  if (!showApi.value && !showDoc.value) return true
  // 仅单类型时不重复显示总计
  return false
})

const tooltipContent = computed(() => {
  const tipItems = []
  if (showApi.value) {
    tipItems.push($i18nBundle('api.label.apiCount', [apiText.value]))
  }
  if (showDoc.value) {
    tipItems.push($i18nBundle('api.label.docCount', [docText.value]))
  }
  if (showTotal.value || !tipItems.length) {
    tipItems.push($i18nBundle('api.label.totalCount', [totalText.value]))
  }
  return tipItems.join(' | ')
})
</script>

<template>
  <el-tooltip
    :content="tooltipContent"
    placement="top"
  >
    <span
      class="doc-count-tag"
      :class="{ pointer }"
      @click="emit('click', $event)"
    >
      <span
        v-if="showApi"
        class="count-item"
      >
        <common-icon
          icon="custom-api"
          style="color: #10b981;"
          :size="iconSize"
        />
        <span>{{ apiText }}</span>
      </span>
      <span
        v-if="showDoc"
        class="count-item"
      >
        <common-icon
          icon="custom-markdown"
          style="color: #8b5cf6;"
          :size="iconSize"
        />
        <span>{{ docText }}</span>
      </span>
      <span
        v-if="showTotal"
        class="count-item font-weight-bold"
      >
        <common-icon
          icon="Files"
          style="color: #3b82f6;"
          :size="iconSize"
        />
        <span>{{ totalText }}</span>
      </span>
    </span>
  </el-tooltip>
</template>

<style scoped>
.doc-count-tag {
  font-size: 12px;
  line-height: 1;
  height: 24px;
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  user-select: none;
  background-color: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 0 8px;
  color: var(--el-text-color-regular);
  transition: all 0.3s;
  white-space: nowrap;
  vertical-align: middle;
}

.count-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  line-height: 1;
}

.doc-count-tag.pointer {
  cursor: pointer;
}

.doc-count-tag:hover {
  background-color: var(--el-fill-color);
  border-color: var(--el-border-color);
}
</style>
