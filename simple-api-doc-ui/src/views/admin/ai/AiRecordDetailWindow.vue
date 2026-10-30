<script setup>
import { computed, ref } from 'vue'
import { MdPreview } from 'md-editor-v3'
import createDOMPurify from 'dompurify'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { $i18nBundle } from '@/messages'
import { showCodeWindow } from '@/utils/DynamicUtils'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { defineMonacoOptions } from '@/vendors/monaco-editor'
import ApiDocApi from '@/api/ApiDocApi'
import ApiProjectApi from '@/api/ApiProjectApi'
import 'md-editor-v3/lib/preview.css'

const visible = defineModel({ type: Boolean, default: false })
const purifier = createDOMPurify(window)
purifier.addHook('uponSanitizeAttribute', (node, data) => {
  if (node.nodeName === 'A' && data.attrName === 'href' && /^doc:/i.test(data.attrValue)) {
    node.setAttribute('data-record-doc', /^doc:\/\/([1-9]\d*)$/i.exec(data.attrValue)?.[1] || 'unavailable')
    data.attrValue = '#'
  }
})
const sanitize = html => purifier.sanitize(html)
const props = defineProps({ record: { type: Object, required: true }, configName: { type: String, default: '' }, statusOption: { type: Object, required: true } })
const activeTab = ref('result')
const router = useRouter()
const openingDoc = ref(false)
const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')
const isMarkdown = computed(() => ['agent_chat', 'test_config'].includes(props.record.cacheType))
const parseJson = value => {
  try { return typeof value === 'string' ? JSON.parse(value) : value } catch { return null }
}
const execution = computed(() => parseJson(props.record.executionDetail))
const formatJson = value => {
  const parsed = parseJson(value)
  return parsed == null ? String(value ?? '') : JSON.stringify(parsed, null, 2)
}
const jsonResponse = computed(() => {
  const parsed = parseJson(props.record.cacheValue)
  return parsed == null ? null : JSON.stringify(parsed, null, 2)
})
const responseHeight = computed(() => Math.min(460, Math.max(160, (jsonResponse.value?.split('\n').length || 1) * 22 + 24)))
const responseOptions = defineMonacoOptions({
  readOnly: true,
  domReadOnly: true,
  minimap: { enabled: false },
  fontSize: 13,
  lineHeight: 22,
  padding: { top: 12, bottom: 12 },
  wordWrap: 'off',
  renderLineHighlight: 'none',
  overviewRulerLanes: 0,
  hideCursorInOverviewRuler: true
})
const viewSource = (value, language = 'json') => showCodeWindow(language === 'json' ? formatJson(value) : value, { language, readOnly: true })
const steps = computed(() => {
  const result = []
  for (const step of Array.isArray(execution.value?.steps) ? execution.value.steps : []) {
    if (!step || typeof step !== 'object' || (step.type === 'model' && step.status === 'success')) continue
    const args = parseJson(step.args)
    const summary = parseJson(step.summary)
    const titleKey = { search_docs: 'aiRecordSearch', get_doc: 'aiRecordRead', model: 'aiRecordModelCall', overview: 'aiRecordOverview' }[step.tool || step.type] || 'aiRecordTool'
    const text = [step.tool === 'search_docs' ? args?.keywords : '', step.tool === 'get_doc' && args?.docId ? `#${args.docId}` : '', summary?.total != null ? $i18nBundle('api.label.aiRecordHits', [summary.total]) : '', summary?.summary || (typeof step.summary === 'string' && !summary ? step.summary : '')].filter(Boolean).join(' · ')
    const previous = step.tool === 'get_doc' && args?.docId && result.find(item => item.docId === args.docId)
    if (previous) {
      previous.duration += Number(step.duration) || 0
      if (step.status === 'failed') { previous.status = 'failed'; previous.text = text }
    } else {
      result.push({ title: $i18nBundle(`api.label.${titleKey}`), text, status: step.status, duration: Number(step.duration) || 0, docId: step.tool === 'get_doc' ? args?.docId : null })
    }
  }
  return result
})
const statusLabel = status => $i18nBundle(`api.label.${{ success: 'aiCacheStatusSuccess', failed: 'aiCacheStatusFailed', skipped: 'aiRecordSkipped' }[status] || 'aiRecordInterrupted'}`)
const finishReason = computed(() => {
  const key = { max_rounds: 'aiRecordMaxRounds', repeated_calls: 'aiRecordRepeatedCalls', incomplete: 'aiRecordIncomplete', interrupted: 'aiRecordInterrupted', failed: 'aiCacheStatusFailed' }[execution.value?.finishReason]
  return key ? $i18nBundle(`api.label.${key}`) : ''
})
const openDocument = async event => {
  const link = event.target.closest('a[data-record-doc]')
  if (!link) return
  event.preventDefault()
  event.stopPropagation()
  if (openingDoc.value || event.type === 'auxclick') return
  const id = Number(link.dataset.recordDoc)
  openingDoc.value = true
  try {
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error('unavailable')
    const { success, resultData: doc } = await ApiDocApi.getById(id, { showErrorMessage: false })
    if (!success || !doc || doc.modifyFrom || (props.record.projectId && String(props.record.projectId) !== String(doc.projectId))) throw new Error('unavailable')
    const project = await ApiProjectApi.getById(doc.projectId, { showErrorMessage: false })
    if (!project.success || !project.resultData?.projectCode) throw new Error('unavailable')
    await router.push({ path: `/api/projects/${encodeURIComponent(project.resultData.projectCode)}`, query: { docId: doc.id } })
    visible.value = false
  } catch {
    ElMessage.info($i18nBundle('api.msg.aiRecordDocUnavailable'))
  } finally { openingDoc.value = false }
}
</script>

<template>
  <common-window
    v-model="visible"
    width="1100px"
    :title="$t('api.label.aiCacheManagement')"
    :show-cancel="false"
    :ok-label="$t('common.label.close')"
    show-fullscreen
    append-to-body
    destroy-on-close
  >
    <div class="record-content">
      <div class="record-meta">
        <el-tag :type="statusOption.type">
          {{ $t(statusOption.labelKey) }}
        </el-tag>
        <span>{{ configName }} · {{ record.modelName }}</span>
        <span>{{ record.costTime ?? '-' }} ms · {{ record.totalTokens ?? '-' }} Tokens</span>
      </div>
      <el-tabs v-model="activeTab">
        <el-tab-pane
          :label="$t('api.label.aiCacheValue')"
          name="result"
        >
          <section
            v-if="record.prompt"
            class="record-section record-request"
          >
            <h4>{{ $t('api.label.aiRecordInput') }}</h4>
            <pre class="record-code record-prompt">{{ record.prompt }}</pre>
          </section>
          <section class="record-section">
            <div class="record-section-header">
              <h4>{{ $t('api.label.aiRecordResponse') }}</h4>
              <div class="record-actions">
                <el-button
                  :disabled="!record.cacheValue"
                  size="small"
                  @click="viewSource(record.cacheValue, isMarkdown ? 'markdown' : 'json')"
                >
                  {{ $t('api.label.aiRecordViewSource') }}
                </el-button>
                <el-tooltip
                  :disabled="!!record.rawResponse"
                  :content="$t('api.label.aiRecordNoRawResponse')"
                >
                  <span>
                    <el-button
                      :disabled="!record.rawResponse"
                      size="small"
                      @click="viewSource(record.rawResponse)"
                    >
                      {{ $t('api.label.aiRecordResponseJson') }}
                    </el-button>
                  </span>
                </el-tooltip>
              </div>
            </div>
            <el-alert
              v-if="record.errorMessage"
              :title="record.errorMessage"
              type="error"
              :closable="false"
            />
            <div
              @click.capture="openDocument"
              @auxclick.capture="openDocument"
            >
              <vue-monaco-editor
                v-if="record.cacheValue && jsonResponse !== null"
                class="record-json"
                :value="jsonResponse"
                language="json"
                :theme="theme === 'dark' ? 'vs-dark' : 'vs'"
                :options="responseOptions"
                :height="`${responseHeight}px`"
              />
              <md-preview
                v-else-if="isMarkdown && record.cacheValue"
                editor-id="ai-record-detail"
                :model-value="record.cacheValue"
                :theme="theme"
                :sanitize="sanitize"
                class="md-doc-container"
              />
              <pre
                v-else-if="record.cacheValue"
                class="record-code"
              >{{ formatJson(record.cacheValue) }}</pre>
              <el-empty
                v-else
                :description="$t('api.label.aiRecordNoResult')"
                :image-size="60"
              />
            </div>
          </section>
        </el-tab-pane>
        <el-tab-pane
          v-if="record.executionDetail"
          :label="$t('api.label.aiRecordExecution')"
          name="execution"
        >
          <div class="record-meta">
            <span>{{ $t('api.label.aiRecordModelCall') }}: {{ execution?.modelCalls ?? '-' }}</span>
            <span>{{ $t('api.label.aiRecordTool') }}: {{ execution?.toolCalls ?? '-' }}</span>
            <el-button
              link
              type="primary"
              @click="viewSource(record.executionDetail)"
            >
              {{ $t('api.label.aiRecordRawExecution') }}
            </el-button>
          </div>
          <p class="record-meta">
            {{ $t('api.label.aiRecordExecutionSummary') }}
          </p>
          <p v-if="finishReason">
            {{ finishReason }}
          </p>
          <p v-if="execution?.truncated">
            {{ $t('api.label.aiRecordTruncated') }}
          </p>
          <p v-if="execution?.usageComplete === false">
            {{ $t('api.label.aiRecordPartialUsage') }}
          </p>
          <div
            v-for="(step, index) in steps"
            :key="index"
            class="record-step"
          >
            <strong>{{ step.title }}</strong>
            <span>{{ step.text }}</span>
            <el-text :type="step.status === 'failed' ? 'danger' : 'info'">
              {{ statusLabel(step.status) }} · {{ step.duration }} ms
            </el-text>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </common-window>
</template>

<style scoped>
.record-content { width: 100%; min-width: 0; }
.record-meta, .record-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; color: var(--el-text-color-secondary); font-size: 13px; }
.record-actions { gap: 8px; }
.record-actions .el-button + .el-button { margin-left: 0; }
.record-section + .record-section { margin-top: 24px; }
.record-section h4 { margin: 0; font-size: 14px; font-weight: 600; color: var(--el-text-color-primary); }
.record-section-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
.record-request { padding: 16px 18px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; background: var(--el-fill-color-light); }
.record-json { overflow: hidden; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; }
.record-code { margin: 12px 0 0; font-family: var(--el-font-family); font-size: 13px; line-height: 1.75; white-space: pre-wrap; overflow-wrap: anywhere; max-height: 460px; overflow: auto; color: var(--el-text-color-regular); }
.record-prompt { max-height: 240px; }
.record-step { display: flex; align-items: baseline; flex-wrap: wrap; gap: 10px; padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter); overflow-wrap: anywhere; }
.record-step > span { flex: 1; }
</style>
