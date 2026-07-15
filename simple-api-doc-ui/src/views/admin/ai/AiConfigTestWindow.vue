<script setup>
import { ref, computed } from 'vue'
import { testAiConfig } from '@/api/AiConfigApi'
import { ElMessage } from 'element-plus'
import { $i18nBundle } from '@/messages'

const props = defineProps({
  configId: {
    type: Number,
    required: true
  }
})

const visible = defineModel({
  type: Boolean,
  default: false
})

const formData = ref({
  prompt: $i18nBundle('api.msg.defaultPrompt'),
  result: ''
})

const testMetrics = ref(null)

const formOptions = computed(() => {
  return [
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

  testAiConfig(props.configId, { userMessage: formData.value.prompt.trim() }, { timeout: 30000 }).then(res => {
    if (res.success) {
      formData.value.result = res.resultData?.content || $i18nBundle('api.msg.noContent')
      testMetrics.value = res.resultData
      ElMessage.success($i18nBundle('api.msg.testSuccess'))
    } else {
      formData.value.result = $i18nBundle('api.msg.testFailed', [res.message || $i18nBundle('common.msg.unknownError')])
      ElMessage.error(res.message || $i18nBundle('api.msg.testFailed', ['']))
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
    <el-container class="flex-column">
      <common-form
        class="form-edit-width-100"
        :model="formData"
        :options="formOptions"
        :show-buttons="false"
        label-width="100px"
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
