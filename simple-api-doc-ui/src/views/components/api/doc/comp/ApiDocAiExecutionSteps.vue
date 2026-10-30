<script setup>
defineProps({
  steps: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  answerStatus: { type: String, default: '' }
})
const expanded = defineModel('expanded', { type: Boolean, default: true })
</script>

<template>
  <div
    v-if="steps.length > 0"
    class="ai-thought-box"
  >
    <div
      class="ai-thought-header"
      @click="expanded = !expanded"
    >
      <div class="ai-thought-title">
        <common-icon
          v-if="loading"
          icon="Loading"
          class="rotating-icon"
          color="var(--el-color-primary)"
        />
        <common-icon
          v-else-if="answerStatus === 'ERROR' || steps.some(s => s.status === 'error')"
          icon="CircleCloseFilled"
          color="var(--el-color-danger)"
        />
        <common-icon
          v-else-if="answerStatus === 'INCOMPLETE'"
          icon="WarningFilled"
          color="var(--el-color-warning)"
        />
        <common-icon
          v-else-if="answerStatus === 'SUCCESS'"
          icon="CircleCheckFilled"
          color="var(--el-color-success)"
        />
        <common-icon
          v-else
          icon="InfoFilled"
          color="var(--el-text-color-secondary)"
        />
        <span class="thought-title-text">{{ $t('api.label.aiRetrievalSteps') }}</span>
        <span class="thought-count-badge">{{ steps.length }}</span>
        <span
          v-if="loading"
          class="thought-status-tag running"
        >{{ $t('api.label.aiRunning') }}...</span>
        <span
          v-else-if="answerStatus === 'INCOMPLETE'"
          class="thought-status-tag running"
        >{{ $t('api.label.aiIncompleteAnswer') }}</span>
        <span
          v-else-if="answerStatus === 'INTERRUPTED'"
          class="step-status-text interrupted"
        >{{ $t('api.label.aiRecordInterrupted') }}</span>
      </div>
      <div class="ai-thought-toggle">
        <common-icon
          icon="ArrowDown"
          class="toggle-arrow"
          :class="{ 'is-collapsed': !expanded }"
        />
      </div>
    </div>

    <el-collapse-transition>
      <div
        v-show="expanded"
        class="ai-thought-body"
      >
        <div class="thought-timeline">
          <div
            v-for="(st, idx) in steps"
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
                v-if="idx < steps.length - 1"
                class="step-line"
              />
            </div>
            <div class="step-content-col">
              <div class="step-header">
                <span class="step-label">
                  {{ st.label }}
                  <template v-if="st.docId && st.status === 'error'">
                    #{{ st.docId }}
                  </template>
                </span>
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
                <span
                  v-else-if="st.status === 'skipped'"
                  class="step-status-text"
                >{{ $t('api.label.aiRecordSkipped') }}</span>
                <span
                  v-else-if="st.status === 'interrupted'"
                  class="step-status-text interrupted"
                >{{ $t('api.label.aiRecordInterrupted') }}</span>
                <span
                  v-if="st.duration != null"
                  class="step-duration"
                >{{ st.duration }} ms</span>
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
</template>

<style scoped>
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
.step-dot.skipped {
  background: var(--el-color-warning);
}
.step-dot.interrupted {
  background: var(--el-text-color-secondary);
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
.step-status-text.interrupted, .step-duration {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.step-duration {
  margin-left: auto;
  white-space: nowrap;
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
</style>
