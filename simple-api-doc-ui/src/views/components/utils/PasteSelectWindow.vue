<script setup>
import { computed, ref, watch } from 'vue'
import { debounce } from 'lodash-es'
import { useMonacoEditorOptions } from '@/vendors/monaco-editor'
import { useShareDocTheme } from '@/services/api/ApiFolderService'
import { parsePasteLines, matchTreeNodes } from '@/services/api/PasteSelectService'
import { $i18nBundle } from '@/messages'

const props = defineProps({
  treeNodes: {
    type: Array,
    default: () => []
  },
  nodeKey: {
    type: String,
    default: 'value'
  },
  isNodeDisabled: {
    type: Function,
    default: undefined
  }
})

const emit = defineEmits(['applyKeys'])

const showWindow = defineModel({ type: Boolean, default: false })

const { monacoTheme } = useShareDocTheme()
const fullscreen = ref(false)
const appendMode = ref(true)
const showUnmatched = ref(false)
const emptyResult = () => ({ matchedKeys: [], unmatchedLines: [] })
const matchResult = ref(emptyResult())

const { contentRef: pasteText, editorRef, monacoEditorOptions } = useMonacoEditorOptions({
  language: 'text',
  readOnly: false,
  autoCheckLang: false,
  formatOnPaste: false,
  wordWrap: 'on',
  minimap: { enabled: false }
})

const editorHeight = computed(() => {
  return fullscreen.value ? 'calc(100dvh - 280px)' : '280px'
})

const doMatch = debounce(() => {
  const text = pasteText.value
  if (!text?.trim()) {
    matchResult.value = emptyResult()
    return
  }
  const lines = parsePasteLines(text)
  matchResult.value = matchTreeNodes(props.treeNodes, lines, {
    nodeKey: props.nodeKey,
    isNodeDisabled: props.isNodeDisabled
  })
}, 300)

watch([pasteText, () => props.treeNodes, () => props.nodeKey], () => {
  if (showWindow.value) {
    doMatch()
  }
}, { flush: 'post' })

const matchedCount = computed(() => matchResult.value.matchedKeys.length)
const unmatchedCount = computed(() => matchResult.value.unmatchedLines.length)

const okLabel = computed(() => $i18nBundle('common.label.pasteApply', [matchedCount.value]))

const applySelection = () => {
  if (!matchedCount.value) return false
  emit('applyKeys', matchResult.value.matchedKeys, appendMode.value)
  showWindow.value = false
}

const resetState = () => {
  doMatch.cancel()
  pasteText.value = ''
  matchResult.value = emptyResult()
  showUnmatched.value = false
  fullscreen.value = false
}

watch(showWindow, (val) => {
  if (!val) {
    resetState()
  }
})
</script>

<template>
  <common-window
    v-model="showWindow"
    v-model:fullscreen="fullscreen"
    default-cls="flex-column"
    :title="$t('common.label.pasteSelect')"
    width="700px"
    append-to-body
    destroy-on-close
    show-fullscreen
    :show-ok="matchedCount > 0"
    :ok-label="okLabel"
    :ok-click="applySelection"
    :cancel-label="$t('common.label.close')"
  >
    <div class="paste-select-container">
      <div class="paste-select-options">
        <el-text
          type="info"
          size="small"
        >
          {{ $t('common.label.pastePlaceholder') }}
        </el-text>
        <el-checkbox v-model="appendMode">
          {{ $t('common.label.pasteAppend') }}
        </el-checkbox>
      </div>

      <vue-monaco-editor
        v-model:value="pasteText"
        :theme="monacoTheme"
        language="text"
        :height="editorHeight"
        :options="monacoEditorOptions"
        @mount="editorRef = $event"
      />

      <div
        v-if="pasteText?.trim()"
        class="paste-select-result"
      >
        <span class="paste-select-label">{{ $t('common.label.pasteMatchResult') }}</span>
        <span class="paste-result-stats">
          <el-text type="success">
            <common-icon
              icon="CheckCircleFilled"
              :size="14"
            />
            {{ $t('common.label.pasteMatched', [matchedCount]) }}
          </el-text>
          <el-text
            v-if="unmatchedCount > 0"
            type="danger"
          >
            <common-icon
              icon="CancelFilled"
              :size="14"
            />
            <el-link
              type="danger"
              underline="never"
              @click="showUnmatched = !showUnmatched"
            >
              {{ $t('common.label.pasteUnmatched', [unmatchedCount]) }}
              <common-icon
                :icon="showUnmatched ? 'ArrowDropUpFilled' : 'ArrowDropDownFilled'"
                :size="16"
              />
            </el-link>
          </el-text>
        </span>
      </div>

      <el-input
        v-if="showUnmatched && unmatchedCount > 0"
        :model-value="matchResult.unmatchedLines.join('\n')"
        type="textarea"
        readonly
        resize="none"
        :rows="Math.min(unmatchedCount, 5)"
        class="paste-unmatched-list"
      />
    </div>
  </common-window>
</template>

<style scoped>
.paste-select-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.paste-select-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.paste-select-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.paste-select-result {
  display: flex;
  align-items: center;
  gap: 10px;
}

.paste-result-stats {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.paste-result-stats .el-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.paste-unmatched-list :deep(.el-textarea__inner) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-color-danger);
}
</style>
