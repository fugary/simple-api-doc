<script setup>
import { ref, computed, onMounted } from 'vue'
import { testAiConfig, loadAiModels, AiConfigApi } from '@/api/AiConfigApi'
import { ElMessage } from 'element-plus'
import { $i18nBundle } from '@/messages'

const props = defineProps({
  config: {
    type: [Number, Object],
    default: null
  },
  configId: {
    type: [Number, Object],
    default: null
  }
})

const targetConfig = computed(() => props.config || props.configId)
const initialConfig = typeof targetConfig.value === 'object' ? targetConfig.value : null

const loadedConfig = ref(null)
const currentConfig = computed(() => loadedConfig.value || (typeof targetConfig.value === 'object' ? targetConfig.value : null))
const configName = computed(() => currentConfig.value?.configName || '')
const provider = computed(() => currentConfig.value?.provider || '')
const providerTagType = computed(() => {
  const typeMap = {
    OPENAI: 'success',
    ANTHROPIC: 'warning',
    GEMINI: ''
  }
  return typeMap[provider.value] !== undefined ? typeMap[provider.value] : 'info'
})

const visible = defineModel({
  type: Boolean,
  default: false
})

const formData = ref({
  prompt: $i18nBundle('api.msg.defaultPrompt'),
  model: initialConfig?.defaultModel || '',
  result: ''
})

const testMetrics = ref(null)
const modelListLoading = ref(false)
const modelList = ref(initialConfig?.defaultModel ? [initialConfig.defaultModel] : [])

const fetchModels = async () => {
  if (!targetConfig.value) {
    return
  }
  modelListLoading.value = true
  try {
    const res = await loadAiModels(targetConfig.value, { showErrorMessage: false }).catch(err => err?.data || err)
    if (res?.success && Array.isArray(res.resultData)) {
      modelList.value = [
        ...new Set([
          ...(formData.value.model?.trim() ? [formData.value.model.trim()] : []),
          ...res.resultData
        ])
      ]
    } else {
      ElMessage.error(res?.message || $i18nBundle('api.msg.loadModelsFailed', [$i18nBundle('common.msg.unknownError')]))
    }
  } catch (err) {
    ElMessage.error(err?.message || $i18nBundle('api.msg.loadModelsFailed', [$i18nBundle('common.msg.networkError')]))
  } finally {
    modelListLoading.value = false
  }
}

onMounted(async () => {
  if (typeof targetConfig.value === 'number') {
    const res = await AiConfigApi.getById(targetConfig.value)
    if (res.success && res.resultData) {
      loadedConfig.value = res.resultData
      if (!formData.value.model && res.resultData.defaultModel) {
        formData.value.model = res.resultData.defaultModel
      }
      if (res.resultData.defaultModel && !modelList.value.includes(res.resultData.defaultModel)) {
        modelList.value = [res.resultData.defaultModel, ...modelList.value]
      }
    }
  }
})

const formOptions = computed(() => {
  return [
    {
      labelKey: 'api.label.testModel',
      prop: 'model',
      type: 'select',
      tooltip: $i18nBundle('api.label.loadModels'),
      tooltipIcon: 'Refresh',
      tooltipFunc: fetchModels,
      required: true,
      attrs: {
        filterable: true,
        allowCreate: true,
        defaultFirstOption: true,
        loading: modelListLoading.value,
        clearable: true,
        placeholder: $i18nBundle('api.msg.selectOrInputModel')
      },
      children: [
        ...new Set([
          ...(formData.value.model?.trim() ? [formData.value.model.trim()] : []),
          ...modelList.value
        ])
      ].map(m => ({ label: m, value: m }))
    },
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
      attrs: {
        type: 'textarea',
        rows: 10,
        readonly: true,
        placeholder: $i18nBundle('api.msg.testResultPlaceholder')
      }
    }
  ]
})
const testLoading = ref(false)
const handleTest = () => {
  if (!formData.value.prompt?.trim()) {
    ElMessage.warning($i18nBundle('api.msg.inputPrompt'))
    return false
  }

  formData.value.result = ''
  testMetrics.value = null
  testLoading.value = true

  const reqBody = {
    userMessage: formData.value.prompt.trim(),
    model: formData.value.model || undefined
  }

  testAiConfig(targetConfig.value, reqBody, { timeout: 30000 }).then(res => {
    if (res.success) {
      formData.value.result = res.resultData?.content || $i18nBundle('api.msg.noContent')
      testMetrics.value = res.resultData
      ElMessage.success($i18nBundle('api.msg.testSuccess'))
    } else {
      formData.value.result = $i18nBundle('api.msg.testFailed', [res.message || $i18nBundle('common.msg.unknownError')])
      ElMessage.error($i18nBundle('api.msg.testFailed', [res.message || $i18nBundle('common.msg.unknownError')]))
    }
  }).catch(error => {
    formData.value.result = $i18nBundle('api.msg.requestError', [error.message || $i18nBundle('common.msg.networkError')])
  }).finally(() => {
    testLoading.value = false
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
    <template #header="{ titleId, titleClass }">
      <span
        :id="titleId"
        class="el-dialog__title"
        :class="titleClass"
      >
        <span class="margin-right2">{{ $t('common.label.test') }}</span>
        <span
          v-if="configName"
          class="margin-right2"
        >{{ configName }}</span>
        <el-tag
          v-if="provider"
          :type="providerTagType"
          size="small"
        >
          {{ provider }}
        </el-tag>
      </span>
    </template>
    <el-container class="flex-column">
      <common-form
        class="form-edit-width-100"
        :model="formData"
        :options="formOptions"
        :show-buttons="false"
        label-width="140px"
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
</style>
