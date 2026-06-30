<script setup>
import { ref, reactive } from 'vue'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import { getAiStatus } from '@/api/AiCacheApi'

const showDialog = ref(false)
const shareConfigStore = useShareConfigStore()

const formData = reactive({
  provider: 'openapi-sampler',
  useExample: true,
  useDescription: false
})
const schemaData = ref(null)
const schemaType = ref('')
const currentPreferenceId = ref('')

let promiseResolve = null
let promiseReject = null

const originalChildren = [
  { label: 'openapi-sampler', value: 'openapi-sampler' },
  { label: 'mock.js', value: 'mockjs' },
  { label: 'json-schema-faker', value: 'json-schema-faker' },
  { label: 'ai-generator', value: 'ai' }
]

const formOptions = reactive([
  {
    labelKey: 'api.label.generateEngine',
    prop: 'provider',
    type: 'select',
    children: [...originalChildren],
    attrs: {
      clearable: false
    }
  },
  {
    labelKey: 'api.label.useExample',
    prop: 'useExample',
    type: 'switch'
  },
  {
    labelKey: 'api.label.useDescription',
    prop: 'useDescription',
    type: 'switch'
  }
])
const showGenerateSampleWindow = (schemaBody, type, preferenceId) => {
  schemaData.value = schemaBody
  schemaType.value = type
  currentPreferenceId.value = preferenceId
  const isShare = !!shareConfigStore.sharePreferenceView[preferenceId]?.isShare
  formOptions[0].children = isShare ? originalChildren.filter(item => item.value !== 'ai') : originalChildren
  const savedConfig = preferenceId ? (shareConfigStore.sharePreferenceView[preferenceId]?.apiGenerateSampleConfig || {}) : {}
  Object.assign(formData, {
    provider: 'openapi-sampler',
    useExample: true,
    useDescription: false,
    ...savedConfig
  })

  if (isShare && formData.provider === 'ai') {
    formData.provider = 'openapi-sampler'
  }

  if (isShare) {
    showDialog.value = true
  } else {
    const aiOption = formOptions[0].children.find(item => item.value === 'ai')
    getAiStatus({ preferenceId: currentPreferenceId.value }).then(res => {
      if (res && res.success) {
        if (aiOption) aiOption.disabled = !res.resultData
        if (!res.resultData && formData.provider === 'ai') formData.provider = 'openapi-sampler'
      }
    }).catch(() => {
      if (aiOption) aiOption.disabled = true
      if (formData.provider === 'ai') formData.provider = 'openapi-sampler'
    }).finally(() => {
      showDialog.value = true
    })
  }
  return new Promise((resolve, reject) => {
    promiseResolve = resolve
    promiseReject = reject
  })
}

const handleOk = () => {
  if (currentPreferenceId.value) {
    const preference = shareConfigStore.sharePreferenceView[currentPreferenceId.value] || {}
    preference.apiGenerateSampleConfig = { ...formData }
    shareConfigStore.sharePreferenceView[currentPreferenceId.value] = preference
  }
  if (promiseResolve) {
    promiseResolve({ mode: formData.provider, useExample: formData.useExample, useDescription: formData.useDescription })
  }
  return true
}

const handleCancel = () => {
  if (promiseReject) {
    promiseReject('cancel')
  }
  return true
}

defineExpose({ showGenerateSampleWindow })
</script>

<template>
  <common-window
    v-model="showDialog"
    :title="$t('common.label.generateData') || '生成测试数据'"
    width="600px"
    :ok-click="handleOk"
    :cancel-click="handleCancel"
    :close-click="handleCancel"
  >
    <common-form
      :model="formData"
      :options="formOptions"
      label-width="160px"
      class="form-edit-width-100"
      :show-buttons="false"
    />
  </common-window>
</template>

<style scoped>
</style>
