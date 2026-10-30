<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import createDOMPurify from 'dompurify'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import 'md-editor-v3/lib/preview.css'

const props = defineProps({
  content: { type: String, default: '' },
  streaming: { type: Boolean, default: false },
  sanitize: { type: Function, default: undefined }
})
const mode = ref('preview')
const displayedContent = ref('')
const globalConfigStore = useGlobalConfigStore()
const theme = computed(() => globalConfigStore.isDarkTheme ? 'dark' : 'light')
const emit = defineEmits(['doc-click'])
const purifier = createDOMPurify(window)
purifier.addHook('uponSanitizeAttribute', (node, data) => {
  if (node.nodeName === 'A' && data.attrName === 'href' && /^doc:/i.test(data.attrValue)) {
    const id = /^doc:\/\/([1-9]\d*)$/i.exec(data.attrValue)?.[1]
    if (id && !props.streaming) node.setAttribute('data-ai-doc', id)
    data.attrValue = '#'
  }
})
const sanitizeContent = html => props.sanitize ? props.sanitize(html) : purifier.sanitize(html)
const openDoc = event => {
  const link = event.target.closest('a[data-ai-doc]')
  if (!link) return
  event.preventDefault()
  event.stopPropagation()
  if (!props.streaming && event.type !== 'auxclick') emit('doc-click', Number(link.dataset.aiDoc))
}
let renderTimer
// 合并高频增量，结束时立即显示最终正文，不重播已收到的内容。
watch([() => props.content, () => props.streaming], ([content, streaming]) => {
  if (!streaming || !content) {
    clearTimeout(renderTimer)
    renderTimer = undefined
    displayedContent.value = content
  } else if (!renderTimer) {
    renderTimer = setTimeout(() => {
      displayedContent.value = props.content
      renderTimer = undefined
    }, 50)
  }
}, { immediate: true })
onUnmounted(() => clearTimeout(renderTimer))
</script>

<template>
  <div
    class="ai-answer-viewer"
    @click.capture="openDoc"
    @auxclick.capture="openDoc"
  >
    <div class="answer-toolbar">
      <el-radio-group
        v-model="mode"
        size="small"
      >
        <el-radio-button value="preview">
          {{ $t('common.label.preview') }}
        </el-radio-button>
        <el-radio-button value="source">
          {{ $t('api.label.aiAnswerSource') }}
        </el-radio-button>
      </el-radio-group>
      <span
        v-if="streaming && content.trim()"
        class="answer-progress"
        role="status"
      >
        <common-icon
          icon="Loading"
          class="is-loading"
        />
        {{ $t('api.label.aiAnswerGenerating') }}
      </span>
      <slot name="actions" />
    </div>
    <div
      v-if="streaming && !content.trim()"
      class="answer-waiting"
      role="status"
    >
      <common-icon
        icon="Loading"
        class="is-loading"
      />
      <span>{{ $t('api.label.aiAnswerWaiting') }}</span>
    </div>
    <md-preview
      v-else-if="mode === 'preview'"
      :key="streaming"
      :model-value="displayedContent"
      :theme="theme"
      :sanitize="sanitizeContent"
      class="md-doc-container"
    />
    <pre
      v-else
      class="answer-source"
    >{{ content }}</pre>
  </div>
</template>

<style scoped>
.ai-answer-viewer { width: 100%; min-width: 0; }
.answer-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 8px; }
.answer-progress, .answer-waiting { display: flex; align-items: center; gap: 8px; color: var(--el-text-color-secondary); font-size: 13px; }
.answer-waiting { min-height: 96px; justify-content: center; padding: 12px; border-radius: 6px; background: var(--el-fill-color-light); }
.answer-source { margin: 0; padding: 12px; white-space: pre-wrap; overflow-wrap: anywhere; font-family: monospace; font-size: 13px; line-height: 1.75; color: var(--el-text-color-regular); background: var(--el-fill-color-light); border-radius: 6px; }
.answer-source, :deep(.md-editor-preview-wrapper) { max-height: 460px; overflow: auto; }
</style>
