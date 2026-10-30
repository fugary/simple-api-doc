<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import createDOMPurify from 'dompurify'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { defineMonacoOptions } from '@/vendors/monaco-editor'
import { $i18nBundle } from '@/messages'
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
const modeOptions = computed(() => [
  { label: $i18nBundle('common.label.preview'), value: 'preview' },
  { label: $i18nBundle('api.label.aiAnswerSource'), value: 'source' }
])
const sourceOptions = defineMonacoOptions({
  readOnly: true,
  domReadOnly: true,
  minimap: { enabled: false },
  fontSize: 13,
  lineHeight: 22,
  padding: { top: 12, bottom: 12 },
  wordWrap: 'on',
  renderLineHighlight: 'none',
  overviewRulerLanes: 0,
  hideCursorInOverviewRuler: true
})
const sourceHeight = computed(() => Math.min(460, Math.max(160, displayedContent.value.split('\n').length * 22 + 24)))
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
      <el-segmented
        v-model="mode"
        :options="modeOptions"
        size="small"
      />
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
      <div
        v-if="$slots.actions"
        class="answer-actions"
      >
        <slot name="actions" />
      </div>
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
    <vue-monaco-editor
      v-else
      class="answer-source"
      :value="displayedContent"
      language="markdown"
      :theme="theme === 'dark' ? 'vs-dark' : 'vs'"
      :options="sourceOptions"
      :height="`${sourceHeight}px`"
    />
  </div>
</template>

<style scoped>
.ai-answer-viewer { width: 100%; min-width: 0; }
.answer-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 8px; }
.answer-actions { display: flex; align-items: center; gap: 8px; margin-left: auto; }
.answer-progress, .answer-waiting { display: flex; align-items: center; gap: 8px; color: var(--el-text-color-secondary); font-size: 13px; }
.answer-waiting { min-height: 96px; justify-content: center; padding: 12px; border-radius: 6px; background: var(--el-fill-color-light); }
.answer-source { overflow: hidden; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; }
:deep(.md-editor-preview-wrapper) { max-height: 460px; overflow: auto; }
</style>
