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

const handleTest = async () => {
  if (!formData.value.prompt?.trim()) {
    ElMessage.warning($i18nBundle('api.msg.inputPrompt'))
    return
  }

  formData.value.result = ''
  try {
    const res = await testAiConfig(props.configId, { prompt: formData.value.prompt.trim() }, { loading: true })
    if (res.success) {
      formData.value.result = res.resultData || $i18nBundle('api.msg.noContent')
      ElMessage.success($i18nBundle('api.msg.testSuccess'))
    } else {
      formData.value.result = $i18nBundle('api.msg.testFailed', [res.message || $i18nBundle('common.msg.unknownError')])
      ElMessage.error(res.message || $i18nBundle('api.msg.testFailed', ['']))
    }
  } catch (error) {
    formData.value.result = $i18nBundle('api.msg.requestError', [error.message || $i18nBundle('common.msg.networkError')])
  }
}

const buttons = computed(() => [
  {
    labelKey: 'common.label.close',
    click: () => { visible.value = false }
  },
  {
    labelKey: 'api.label.sendTest',
    type: 'primary',
    click: handleTest
  }
])
</script>

<template>
  <common-window
    v-model="visible"
    :title="$t('common.label.test')"
    width="800px"
    :show-ok="false"
    :show-cancel="false"
    :buttons="buttons"
  >
    <common-form
      class="form-edit-width-100"
      :model="formData"
      :options="formOptions"
      :show-buttons="false"
      label-width="100px"
    />
  </common-window>
</template>

<style scoped>
</style>
