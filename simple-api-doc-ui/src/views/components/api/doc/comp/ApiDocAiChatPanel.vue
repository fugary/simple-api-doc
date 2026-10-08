<script setup>
import { computed, onUnmounted, ref, toRef, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { ElMessage } from 'element-plus'
import { $i18nBundle } from '@/messages'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { streamAgentChat } from '@/api/AiAgentApi'
import { $copyText } from '@/utils'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import { buildAiConfigOptions, useAiModelSelector } from '@/services/api/ApiCommonService'
import { useDocProjectSelector } from '@/services/api/ApiDocSearchService'
import { defineFormOptions } from '@/components/utils'

const props = defineProps({
  project: { type: Object, default: undefined },
  aiState: { type: Object, required: true },
  aiConfigs: { type: Array, default: () => [] },
  defaultConfigId: { type: [Number, String], default: null },
  onSelectDoc: { type: Function, default: undefined }
})

const emit = defineEmits(['switch-to-search'])

const globalConfigStore = useGlobalConfigStore()
const theme = computed(() => globalConfigStore.isDarkTheme ? 'dark' : 'default')

const activeAiState = computed(() => props.aiState)

const formData = props.aiState
if (!props.aiConfigs.some(config => config.id === formData.configId)) {
  formData.configId = props.defaultConfigId || props.aiConfigs[0]?.id || null
  formData.model = ''
}
formData.projectId ||= props.project?.id || null

const {
  syncModelFromConfig,
  buildModelFormOption
} = useAiModelSelector(formData, computed(() => props.aiConfigs))

if (formData.configId) {
  syncModelFromConfig(formData.configId)
}

const configOptions = computed(() => buildAiConfigOptions(props.aiConfigs, props.defaultConfigId))
formData.projectOptions ||= []
const projects = toRef(formData, 'projectOptions')
const { loadProjects, projectSelectAttrs } = useDocProjectSelector(() => formData.projectId, projects)
if (!props.project) {
  watch(projects, list => {
    if (!formData.projectId && list.length) formData.projectId = list[0].id
  })
  loadProjects()
}

const configFormOptions = computed(() => {
  const options = []
  if (!props.project) {
    options.push({
      labelKey: 'api.label.project',
      prop: 'projectId',
      type: 'select',
      children: projects.value.map(p => ({ label: p.projectName, value: p.id })),
      attrs: {
        ...projectSelectAttrs.value,
        placeholder: $i18nBundle('api.msg.selectProjectFirst')
      }
    })
  }
  if (props.aiConfigs.length > 0) {
    options.push({
      labelKey: 'api.label.aiConfigSelect',
      prop: 'configId',
      type: 'select',
      children: configOptions.value,
      attrs: {
        clearable: false
      }
    })
  }
  if (formData.configId) {
    options.push(buildModelFormOption())
  }
  return defineFormOptions(options)
})

const queryInput = computed({
  get: () => activeAiState.value.queryInput || '',
  set: (val) => { activeAiState.value.queryInput = val }
})
const loading = ref(false)
const errorMessage = ref('')
const isStepsExpanded = computed({
  get: () => activeAiState.value.isStepsExpanded ?? true,
  set: (val) => { activeAiState.value.isStepsExpanded = val }
})

// 会话输出状态
const toolSteps = computed({
  get: () => activeAiState.value.toolSteps || [],
  set: (val) => { activeAiState.value.toolSteps = val }
})
const relatedDocs = computed({
  get: () => activeAiState.value.relatedDocs || [],
  set: (val) => { activeAiState.value.relatedDocs = val }
})
const answerContent = computed({
  get: () => activeAiState.value.answerContent || '',
  set: (val) => { activeAiState.value.answerContent = val }
})
let abortController = null

/**
 * 若当前仍处于初始 reasoning 步骤，将其标记为已完成
 */
const finishReasoningStep = () => {
  if (toolSteps.value.length >= 1 && toolSteps.value[0].tool === 'reasoning' && toolSteps.value[0].status === 'running') {
    const steps = [...toolSteps.value]
    steps[0] = { ...steps[0], status: 'finished', summary: steps[0].summary || $i18nBundle('api.msg.aiIntentAnalysisDone') }
    toolSteps.value = steps
  }
}

/**
 * 将最后一个步骤的状态更新为指定值（immutable 方式触发 store 更新）
 */
const updateLastStepStatus = (status) => {
  const steps = [...toolSteps.value]
  const last = steps[steps.length - 1]
  if (last && last.status === 'running') {
    steps[steps.length - 1] = { ...last, status }
    toolSteps.value = steps
  }
}

const runChat = async () => {
  if (loading.value) return
  const query = queryInput.value.trim()
  if (!query) {
    ElMessage.warning($i18nBundle('api.msg.aiQueryEmpty'))
    return
  }

  const targetProjectId = props.project?.id || formData.projectId
  if (!targetProjectId) {
    ElMessage.warning($i18nBundle('api.msg.selectProjectFirst'))
    return
  }

  // 重置单次状态，并立即显示初始"思考与检索过程"
  loading.value = true
  errorMessage.value = ''
  toolSteps.value = [
    {
      tool: 'reasoning',
      label: $i18nBundle('api.label.aiIntentAnalysis'),
      status: 'running'
    }
  ]
  relatedDocs.value = []
  answerContent.value = ''
  isStepsExpanded.value = true

  const controller = new AbortController()
  abortController = controller
  const isCurrentRequest = () => abortController === controller && !controller.signal.aborted
  const onCurrentRequest = callback => data => {
    if (isCurrentRequest()) callback(data)
  }

  try {
    await streamAgentChat(
      {
        query,
        projectId: targetProjectId,
        includeProjectOverview: !!formData.includeProjectOverview,
        configId: formData.configId || undefined,
        model: formData.model?.trim() || undefined
      },
      {
        onStatus: onCurrentRequest((data) => {
          if (toolSteps.value.length > 0 && toolSteps.value[0].tool === 'reasoning') {
            const steps = [...toolSteps.value]
            steps[0] = { ...steps[0], summary: data?.message || data?.text || '' }
            toolSteps.value = steps
          }
        }),
        onToolStart: onCurrentRequest((data) => {
          finishReasoningStep()
          const step = {
            tool: data.tool,
            label: data.tool === 'search_docs' ? $i18nBundle('api.label.aiSearchDocsStep') : $i18nBundle('api.label.aiGetDocStep'),
            detail: data.arguments,
            status: 'running'
          }
          toolSteps.value = [...toolSteps.value, step]
        }),
        onToolEnd: onCurrentRequest((data) => {
          const steps = [...toolSteps.value]
          const last = steps[steps.length - 1]
          if (last) {
            steps[steps.length - 1] = { ...last, status: 'finished', summary: data.summary }
            toolSteps.value = steps
          }
        }),
        onRelatedDocs: onCurrentRequest((docs) => {
          if (Array.isArray(docs)) {
            relatedDocs.value = docs
          }
        }),
        onDelta: onCurrentRequest((data) => {
          finishReasoningStep()
          if (data?.text !== undefined) {
            answerContent.value = data.text
          }
        }),
        onFinish: onCurrentRequest(() => {
          finishReasoningStep()
        }),
        onError: onCurrentRequest((err) => {
          errorMessage.value = err?.message || $i18nBundle('api.msg.aiResponseError')
          updateLastStepStatus('error')
        })
      },
      controller.signal
    )
  } catch (err) {
    if (isCurrentRequest() && err.name !== 'AbortError') {
      errorMessage.value = err.message || $i18nBundle('common.msg.networkError')
      updateLastStepStatus('error')
    }
  } finally {
    if (abortController === controller) {
      loading.value = false
      abortController = null
    }
  }
}

const stopChat = () => {
  if (abortController) {
    const controller = abortController
    abortController = null
    controller.abort()
    loading.value = false
    updateLastStepStatus('finished')
  }
}
onUnmounted(stopChat)

const clearAll = () => {
  stopChat()
  queryInput.value = ''
  toolSteps.value = []
  relatedDocs.value = []
  answerContent.value = ''
  errorMessage.value = ''
}

const handleOpenDoc = (doc) => {
  if (props.onSelectDoc) {
    props.onSelectDoc(doc)
  }
}

const copyPath = (url) => {
  if (url) {
    $copyText(url)
    ElMessage.success($i18nBundle('common.msg.copySuccess'))
  }
}

// 拦截 Markdown 正文中的链接点击，支持点击 doc://{id} 打开对应接口
const handlePreviewClick = (event) => {
  const link = event.target.closest('a')
  if (link) {
    const href = link.getAttribute('href')
    if (href && href.startsWith('doc://')) {
      event.preventDefault()
      const docId = Number(href.replace('doc://', ''))
      const doc = relatedDocs.value.find(item => item.id === docId)
      if (doc) handleOpenDoc(doc)
    }
  }
}

const switchToManualSearch = () => {
  emit('switch-to-search', queryInput.value)
}
</script>

<template>
  <div class="api-ai-panel">
    <!-- 顶部输入与提问区 -->
    <div class="ai-input-card">
      <div class="ai-config-bar">
        <div
          v-if="props.project"
          class="ai-project-badge"
        >
          <span class="ai-scope-tag">
            <common-icon icon="Folder" />
            <span class="project-name">{{ props.project.projectName }}</span>
          </span>
        </div>
        <common-form
          class="ai-config-form"
          :model="formData"
          :options="configFormOptions"
          label-width="auto"
          inline
          :show-buttons="false"
        />
      </div>

      <el-input
        v-model="queryInput"
        type="textarea"
        :rows="2"
        :autosize="{ minRows: 2, maxRows: 4 }"
        :placeholder="$t('api.msg.aiChatPlaceholder')"
        :disabled="loading"
        @keydown.enter.exact.prevent="runChat"
      />
      <div class="ai-input-actions">
        <el-checkbox
          v-model="formData.includeProjectOverview"
          v-common-tooltip:top="$t('api.msg.aiIncludeProjectOverview')"
          :disabled="loading"
        >
          {{ $t('api.label.aiIncludeProjectOverview') }}
        </el-checkbox>
        <div class="ai-btn-group">
          <el-button
            v-if="loading"
            type="danger"
            plain
            size="small"
            @click="stopChat"
          >
            {{ $t('common.label.cancel') }}
          </el-button>
          <el-button
            size="small"
            :disabled="loading || (!queryInput && !answerContent)"
            @click="clearAll"
          >
            {{ $t('common.label.clear') }}
          </el-button>
          <el-button
            type="primary"
            size="small"
            :loading="loading"
            @click="runChat"
          >
            {{ $t('api.label.aiSend') }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 错误警告 -->
    <el-alert
      v-if="errorMessage"
      class="margin-top1"
      type="error"
      show-icon
      :title="errorMessage"
      :closable="false"
    />

    <!-- 深度思考与检索过程（现代流式时间线卡片） -->
    <div
      v-if="toolSteps.length > 0"
      class="ai-thought-box"
    >
      <div
        class="ai-thought-header"
        @click="isStepsExpanded = !isStepsExpanded"
      >
        <div class="ai-thought-title">
          <common-icon
            v-if="loading"
            icon="Loading"
            class="rotating-icon"
            color="var(--el-color-primary)"
          />
          <common-icon
            v-else-if="toolSteps.some(s => s.status === 'error')"
            icon="CircleCloseFilled"
            color="var(--el-color-danger)"
          />
          <common-icon
            v-else
            icon="CircleCheckFilled"
            color="var(--el-color-success)"
          />
          <span class="thought-title-text">{{ $t('api.label.aiRetrievalSteps') }}</span>
          <span class="thought-count-badge">{{ toolSteps.length }}</span>
          <span
            v-if="loading"
            class="thought-status-tag running"
          >{{ $t('api.label.aiRunning') }}...</span>
        </div>
        <div class="ai-thought-toggle">
          <common-icon
            icon="ArrowDown"
            class="toggle-arrow"
            :class="{ 'is-collapsed': !isStepsExpanded }"
          />
        </div>
      </div>

      <el-collapse-transition>
        <div
          v-show="isStepsExpanded"
          class="ai-thought-body"
        >
          <div class="thought-timeline">
            <div
              v-for="(st, idx) in toolSteps"
              :key="idx"
              class="thought-step"
              :class="st.status"
            >
              <div class="step-dot-col">
                <span
                  class="step-dot"
                  :class="st.status"
                />
                <span
                  v-if="idx < toolSteps.length - 1"
                  class="step-line"
                />
              </div>
              <div class="step-content-col">
                <div class="step-header">
                  <span class="step-label">{{ st.label }}</span>
                  <el-tag
                    size="small"
                    class="step-tool-tag"
                    effect="plain"
                    round
                    :type="st.status === 'running' ? 'warning' : st.status === 'error' ? 'danger' : 'info'"
                  >
                    {{ st.tool }}
                  </el-tag>
                  <span
                    v-if="st.status === 'running'"
                    class="step-status-text"
                  >{{ $t('api.label.aiRunning') }}...</span>
                  <span
                    v-else-if="st.status === 'error'"
                    class="step-status-text error"
                  >{{ $t('api.msg.aiResponseError') }}</span>
                </div>
                <div
                  v-if="st.summary"
                  class="step-summary-box"
                >
                  {{ st.summary }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-collapse-transition>
    </div>

    <!-- 关联接口卡片展示区 (置顶展示，方便开发者直接调试或查看) -->
    <div
      v-if="relatedDocs.length > 0"
      class="related-docs-section"
    >
      <div class="section-title">
        <common-icon icon="Link" />
        <span>{{ $t('api.label.aiRelatedDocs') }} ({{ relatedDocs.length }})</span>
      </div>
      <div class="related-docs-grid">
        <div
          v-for="doc in relatedDocs"
          :key="doc.id"
          class="related-doc-card"
        >
          <div class="card-top">
            <api-method-tag
              v-if="doc.docType === 'api'"
              :method="doc.method"
            />
            <el-link
              class="card-doc-name"
              type="primary"
              :underline="false"
              @click="handleOpenDoc(doc)"
            >
              {{ doc.docName }}
            </el-link>
          </div>
          <div
            v-if="doc.url"
            class="card-url"
          >
            <code>{{ doc.url }}</code>
          </div>
          <div class="card-bottom">
            <span class="card-project">{{ doc.projectName }}</span>
            <span
              v-if="doc.folderPath"
              class="card-folder"
            >/ {{ doc.folderPath }}</span>
            <div class="card-actions">
              <el-button
                link
                type="primary"
                size="small"
                @click="handleOpenDoc(doc)"
              >
                {{ $t('api.label.viewDoc') }}
              </el-button>
              <el-button
                v-if="doc.url"
                link
                size="small"
                @click="copyPath(doc.url)"
              >
                {{ $t('common.label.copy') }}
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- AI 方案与调用指南 (Markdown) -->
    <div
      v-if="answerContent"
      class="ai-answer-section"
      @click="handlePreviewClick"
    >
      <div class="section-title">
        <common-icon icon="Document" />
        <span>{{ $t('api.label.aiAnswerTitle') }}</span>
      </div>
      <div class="ai-markdown-wrapper">
        <md-preview
          editor-id="ai-agent-preview"
          :theme="theme"
          :model-value="answerContent"
        />
      </div>

      <!-- 兜底与切换提示 -->
      <div class="ai-fallback-footer">
        <span class="fallback-tip">{{ $t('api.msg.aiResultNotExpected') }}</span>
        <el-button
          link
          type="primary"
          size="small"
          @click="switchToManualSearch"
        >
          {{ $t('api.label.switchToManualSearch') }} →
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.api-ai-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}
.ai-input-card {
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 12px;
}
.ai-config-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 2px;
}
.ai-project-badge {
  display: inline-flex;
  align-items: center;
  margin-right: 16px;
  margin-bottom: 14px;
}
.ai-config-form :deep(.el-form-item) {
  margin-bottom: 14px;
  margin-right: 16px;
}
.ai-config-form :deep(.el-select) {
  min-width: 180px;
}
.ai-input-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-top: 8px;
}
.ai-scope-tag {
  font-size: 12px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  padding: 2px 8px;
  border-radius: 4px;
}
.ai-scope-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ai-btn-group {
  display: flex;
  margin-left: auto;
  gap: 8px;
}

.ai-thought-box {
  margin: 12px 0 16px 0;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-left: 3px solid var(--el-color-primary);
  overflow: hidden;
  transition: all 0.2s ease;
}
.ai-thought-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  background: transparent;
}
.ai-thought-header:hover {
  background: var(--el-fill-color);
}
.ai-thought-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}
.thought-title-text {
  font-size: 13px;
}
.thought-count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  height: 18px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 9px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.thought-status-tag.running {
  font-size: 12px;
  font-weight: normal;
  color: var(--el-color-warning);
}
.ai-thought-toggle {
  display: flex;
  align-items: center;
  color: var(--el-text-color-secondary);
}
.toggle-arrow {
  transition: transform 0.25s ease;
}
.toggle-arrow.is-collapsed {
  transform: rotate(-90deg);
}
.rotating-icon {
  animation: rotate 1s linear infinite;
}
@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.ai-thought-body {
  padding: 2px 14px 14px 14px;
}
.thought-timeline {
  display: flex;
  flex-direction: column;
}
.thought-step {
  display: flex;
  gap: 10px;
  position: relative;
}
.step-dot-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 14px;
  padding-top: 5px;
  flex-shrink: 0;
}
.step-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-success);
}
.step-dot.running {
  background: var(--el-color-warning);
  box-shadow: 0 0 0 3px var(--el-color-warning-light-8);
  animation: pulse-dot 1.5s infinite;
}
.step-dot.error {
  background: var(--el-color-danger);
}
@keyframes pulse-dot {
  0% { transform: scale(0.95); opacity: 0.8; }
  50% { transform: scale(1.15); opacity: 1; }
  100% { transform: scale(0.95); opacity: 0.8; }
}
.step-line {
  flex: 1;
  width: 2px;
  min-height: 18px;
  background: var(--el-border-color-lighter);
  margin-top: 4px;
}
.step-content-col {
  flex: 1;
  min-width: 0;
  padding-bottom: 8px;
}
.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.step-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}
.step-tool-tag {
  font-size: 11px;
  height: 20px;
  padding: 0 6px;
}
.step-status-text {
  font-size: 12px;
  color: var(--el-color-warning);
}
.step-status-text.error {
  color: var(--el-color-danger);
}
.step-summary-box {
  margin-top: 5px;
  padding: 6px 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  word-break: break-word;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}
.related-docs-section {
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
}
.related-docs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 10px;
}
.related-doc-card {
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: box-shadow 0.2s;
}
.related-doc-card:hover {
  box-shadow: var(--el-box-shadow-light);
}
.card-top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-doc-name {
  font-weight: 600;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-url {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-top: 2px;
}
.card-project {
  color: var(--el-text-color-secondary);
}
.card-actions {
  display: flex;
  gap: 4px;
  margin-left: auto;
}
.ai-answer-section {
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 14px;
}
.ai-markdown-wrapper :deep(.md-editor-preview-wrapper) {
  padding: 0;
}
.ai-fallback-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px dashed var(--el-border-color-lighter);
}
.fallback-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
