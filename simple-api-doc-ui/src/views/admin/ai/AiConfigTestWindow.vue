<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { streamTestAiConfig, loadAiModels, AiConfigApi } from '@/api/AiConfigApi'
import { useAiConfigStore } from '@/stores/AiConfigStore'
import { buildAiConfigOptions, useAiModelSelector } from '@/services/api/ApiCommonService'
import { ElMessage } from 'element-plus'
import { $i18nBundle } from '@/messages'
import AiAnswerViewer from '@/views/components/utils/AiAnswerViewer.vue'

const props = defineProps({
  config: {
    type: [Number, Object],
    default: null
  },
  configId: {
    type: [Number, Object],
    default: null
  },
  allowChangeConfig: {
    type: Boolean,
    default: undefined
  }
})

const visible = defineModel({
  type: Boolean,
  default: false
})

const aiConfigStore = useAiConfigStore()
const targetConfig = computed(() => props.config || props.configId)
const isSwitchable = computed(() => {
  if (props.allowChangeConfig !== undefined) {
    return props.allowChangeConfig
  }
  return !props.config && !props.configId
})

const aiConfigs = ref([])
const defaultAiConfigId = ref(null)

const formData = ref({
  configId: null,
  prompt: $i18nBundle('api.msg.defaultPrompt'),
  model: '',
  result: ''
})

const testMetrics = ref(null)

const {
  aiModelList,
  aiModelLoading,
  syncModelFromConfig,
  buildModelFormOption
} = useAiModelSelector(formData, aiConfigs)

const fetchCustomAiModels = async () => {
  const curConfig = aiConfigs.value.find(c => c.id === formData.value.configId) || (typeof targetConfig.value === 'object' ? targetConfig.value : null)
  const targetParam = (typeof curConfig?.id === 'number') ? curConfig.id : curConfig
  if (!targetParam) return
  aiModelLoading.value = true
  try {
    const res = await loadAiModels(targetParam, { showErrorMessage: false }).catch(err => err?.data || err)
    if (res?.success && Array.isArray(res.resultData)) {
      formData.value.model = formData.value.model || curConfig?.defaultModel || res.resultData[0] || ''
      aiModelList.value = [
        ...new Set([
          ...(formData.value.model?.trim() ? [formData.value.model.trim()] : []),
          ...res.resultData
        ])
      ]
      aiConfigStore.saveCachedModels(targetParam, res.resultData)
    } else {
      ElMessage.error(res?.message || $i18nBundle('api.msg.loadModelsFailed', [$i18nBundle('common.msg.unknownError')]))
    }
  } catch (err) {
    ElMessage.error(err?.message || $i18nBundle('api.msg.loadModelsFailed', [$i18nBundle('common.msg.networkError')]))
  } finally {
    aiModelLoading.value = false
  }
}

onMounted(async () => {
  const res = await AiConfigApi.search({ status: 1 })
  let list = (res.success && Array.isArray(res.resultData)) ? res.resultData : []

  if (!isSwitchable.value && targetConfig.value) {
    let current = typeof targetConfig.value === 'object' ? { ...targetConfig.value } : null
    if (typeof targetConfig.value === 'number') {
      const getRes = await AiConfigApi.getById(targetConfig.value)
      if (getRes.success && getRes.resultData) {
        current = getRes.resultData
      }
    }
    if (current) {
      const existsIndex = list.findIndex(item => item.id && item.id === current.id)
      const targetId = current.id ?? 'temp_current'
      current.id = targetId
      if (existsIndex >= 0) {
        list[existsIndex] = current
      } else {
        list = [current, ...list]
      }
      aiConfigs.value = list
      formData.value.configId = targetId
      syncModelFromConfig(targetId)
      return
    }
  }

  aiConfigs.value = list
  const defConfig = list.find(c => c.isDefault === 1) || list[0]
  if (defConfig) {
    defaultAiConfigId.value = defConfig.id
    formData.value.configId = defConfig.id
    syncModelFromConfig(defConfig.id)
  } else if (!isSwitchable.value) {
    ElMessage.warning($i18nBundle('api.msg.noDefaultConfig'))
  }
})

const formOptions = computed(() => {
  return [
    {
      labelKey: 'api.label.aiConfigSelect',
      prop: 'configId',
      type: 'select',
      required: true,
      disabled: !isSwitchable.value,
      children: buildAiConfigOptions(aiConfigs.value, defaultAiConfigId.value),
      attrs: {
        clearable: false
      }
    },
    buildModelFormOption({
      required: true,
      tooltipFunc: fetchCustomAiModels
    }),
    {
      labelKey: 'api.label.testPrompt',
      prop: 'prompt',
      required: true,
      attrs: {
        type: 'textarea',
        rows: 3,
        placeholder: $i18nBundle('api.msg.inputPrompt')
      }
    },
    {
      labelKey: 'api.label.testResult',
      prop: 'result',
      slot: 'result'
    }
  ]
})

const testLoading = ref(false)
const testError = ref('')
const testStatus = ref('')
let abortController
const stopTest = () => {
  if (!abortController) return
  const controller = abortController
  abortController = null
  controller.abort()
  testLoading.value = false
  testStatus.value = 'INTERRUPTED'
}
watch(visible, value => { if (!value) stopTest() })
onUnmounted(stopTest)
const handleTest = () => {
  if (testLoading.value) return false
  if (!formData.value.prompt?.trim()) {
    ElMessage.warning($i18nBundle('api.msg.inputPrompt'))
    return false
  }

  const curConfig = aiConfigs.value.find(c => c.id === formData.value.configId) || (typeof targetConfig.value === 'object' ? targetConfig.value : null)
  const target = (typeof curConfig?.id === 'number') ? curConfig.id : curConfig
  if (!target) {
    ElMessage.warning($i18nBundle('api.msg.noDefaultConfig'))
    return false
  }

  formData.value.result = ''
  testMetrics.value = null
  testError.value = ''
  testStatus.value = ''
  testLoading.value = true

  const reqBody = {
    userMessage: formData.value.prompt.trim(),
    model: formData.value.model || undefined
  }

  const controller = new AbortController()
  abortController = controller
  const isCurrent = () => abortController === controller && !controller.signal.aborted
  streamTestAiConfig(target, reqBody, {
    onDelta: data => {
      if (isCurrent() && typeof data?.text === 'string') formData.value.result += data.text
    },
    onFinish: data => {
      if (!isCurrent()) return
      formData.value.result = data.content ?? formData.value.result
      testMetrics.value = data
      testStatus.value = data.status
      if (data.status === 'SUCCESS') ElMessage.success($i18nBundle('api.msg.testSuccess'))
    },
    onError: error => {
      if (!isCurrent()) return
      testStatus.value = 'ERROR'
      testError.value = error?.message || $i18nBundle('common.msg.unknownError')
    }
  }, controller.signal).catch(error => {
    if (!isCurrent() || error.name === 'AbortError') return
    testStatus.value = 'ERROR'
    testError.value = error.message || $i18nBundle('common.msg.networkError')
  }).finally(() => {
    if (abortController === controller) {
      testLoading.value = false
      abortController = null
    }
  })

  return false
}
</script>

<template>
  <common-window
    v-model="visible"
    :title="$t('common.label.test')"
    width="800px"
    :ok-label="$t('api.label.sendTest')"
    :ok-loading="testLoading"
    :ok-click="handleTest"
    :cancel-label="$t('common.label.close')"
  >
    <el-container class="flex-column">
      <common-form
        class="form-edit-width-100"
        :model="formData"
        :options="formOptions"
        :show-buttons="false"
        label-width="140px"
      >
        <template #result="{ option }">
          <common-form-control
            :model="formData"
            :option="option"
          >
            <ai-answer-viewer
              v-if="visible && (formData.result || testLoading)"
              :content="formData.result"
              :streaming="testLoading"
            >
              <template #actions>
                <el-button
                  v-if="testLoading"
                  size="small"
                  @click="stopTest"
                >
                  {{ $t('api.label.stopTask') }}
                </el-button>
              </template>
            </ai-answer-viewer>
            <span
              v-else
              class="test-result-placeholder"
            >
              {{ $t('api.msg.testResultPlaceholder') }}
            </span>
          </common-form-control>
        </template>
      </common-form>
      <el-alert
        v-if="testError"
        :title="testError"
        type="error"
        :closable="false"
      />
      <el-alert
        v-else-if="testStatus === 'INTERRUPTED' || testStatus === 'INCOMPLETE'"
        :title="$t(testStatus === 'INTERRUPTED' ? 'api.msg.aiStreamInterrupted' : 'api.label.aiIncompleteAnswer')"
        type="warning"
        :closable="false"
      />
      <div
        v-if="testMetrics"
        class="margin-top3"
        style="padding-left: 100px;"
      >
        <el-space
          wrap
          :size="10"
        >
          <el-tag
            v-if="testMetrics.elapsedTime != null"
            type="primary"
            size="small"
          >
            {{ $t('api.label.aiCacheCostTime') }}: {{ testMetrics.elapsedTime }}ms
          </el-tag>
          <el-tag
            v-if="testMetrics.totalTokens != null"
            type="success"
            size="small"
          >
            Tokens: {{ testMetrics.totalTokens }}
          </el-tag>
        </el-space>
      </div>
    </el-container>
  </common-window>
</template>

<style scoped>
.test-result-placeholder { color: var(--el-text-color-placeholder); }
</style>
