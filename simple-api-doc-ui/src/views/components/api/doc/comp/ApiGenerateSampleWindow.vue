<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import { getAiStatus } from '@/api/AiCacheApi'
import { buildAiConfigOptions, useAiModelSelector } from '@/services/api/ApiCommonService'
import { defineFormOptions } from '@/components/utils'

const showDialog = ref(false)
const shareConfigStore = useShareConfigStore()

const formData = reactive({
  provider: 'openapi-sampler',
  configId: null,
  model: '',
  useExample: true,
  useDescription: false
})
const schemaData = ref(null)
const schemaType = ref('')
const currentPreferenceId = ref('')
const sharePreference = computed(() => shareConfigStore.sharePreferenceView[currentPreferenceId.value])
const isShare = computed(() => !!sharePreference.value?.isShare)
const aiConfigs = ref([])
const defaultAiConfigId = ref(null)

const { syncModelFromConfig, buildModelFormOption } = useAiModelSelector(formData, aiConfigs)

watch(() => formData.provider, (val) => {
  if (val === 'ai' && !formData.configId) {
    formData.configId = defaultAiConfigId.value
  }
})

let promiseResolve = null
let promiseReject = null

const originalChildren = [
  { label: 'openapi-sampler', value: 'openapi-sampler' },
  { label: 'mock.js', value: 'mockjs' },
  { label: 'json-schema-faker', value: 'json-schema-faker' },
  { label: 'ai-generator', value: 'ai' }
]

const engineOptions = ref([...originalChildren])

const formOptions = computed(() => {
  const options = [
    {
      labelKey: 'api.label.generateEngine',
      prop: 'provider',
      type: 'select',
      children: [...engineOptions.value],
      attrs: {
        clearable: false
      }
    }
  ]
  if (!isShare.value && formData.provider === 'ai' && aiConfigs.value.length > 0) {
    options.push({
      labelKey: 'api.label.aiConfigSelect',
      prop: 'configId',
      type: 'select',
      children: buildAiConfigOptions(aiConfigs.value, defaultAiConfigId.value),
      attrs: {
        clearable: false
      }
    })
    options.push(buildModelFormOption())
  }
  options.push(
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
  )
  return defineFormOptions(options)
})

const showGenerateSampleWindow = (schemaBody, type, preferenceId) => {
  schemaData.value = schemaBody
  schemaType.value = type
  currentPreferenceId.value = preferenceId
  engineOptions.value = originalChildren.map(item => ({ ...item }))
  aiConfigs.value = []
  defaultAiConfigId.value = null
  const savedConfig = sharePreference.value?.apiGenerateSampleConfig || {}
  Object.assign(formData, {
    provider: 'openapi-sampler',
    configId: null,
    model: '',
    useExample: true,
    useDescription: false,
    ...savedConfig
  })

  if (isShare.value) {
    formData.configId = null
    formData.model = ''
  }
  const disableAi = () => {
    engineOptions.value = originalChildren.filter(item => item.value !== 'ai')
    if (formData.provider === 'ai') formData.provider = 'openapi-sampler'
  }
  if (isShare.value && !sharePreference.value?.aiSampleEnabled) {
    disableAi()
    showDialog.value = true
  } else {
    getAiStatus({ preferenceId: currentPreferenceId.value }).then(res => {
      const data = res?.success ? res.resultData || {} : {}
      const enabled = !!data.enabled && (!isShare.value || !!sharePreference.value?.aiSampleEnabled)
      if (isShare.value) {
        engineOptions.value = originalChildren.filter(item => item.value !== 'ai' || enabled)
      } else {
        engineOptions.value.find(item => item.value === 'ai').disabled = !enabled
        aiConfigs.value = data.configs || []
        defaultAiConfigId.value = data.defaultConfigId || null
        if (enabled && !aiConfigs.value.some(c => c.id === formData.configId)) {
          formData.configId = defaultAiConfigId.value
        }
        syncModelFromConfig(formData.configId)
      }
      if (!enabled && formData.provider === 'ai') formData.provider = 'openapi-sampler'
    }).catch(disableAi).finally(() => {
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
    promiseResolve({
      mode: formData.provider,
      configId: formData.configId,
      model: formData.model || undefined,
      useExample: formData.useExample,
      useDescription: formData.useDescription
    })
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
    width="800px"
    :ok-click="handleOk"
    :cancel-click="handleCancel"
    :close-click="handleCancel"
  >
    <common-form
      :model="formData"
      :options="formOptions"
      label-width="150px"
      class="form-edit-width-100"
      :show-buttons="false"
    />
  </common-window>
</template>

<style scoped>
</style>
