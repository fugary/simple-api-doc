<script setup>
import { ref, reactive } from 'vue'

const showDialog = ref(false)
const formData = reactive({
  provider: 'openapi-sampler',
  useExample: true,
  useDescription: false
})
const schemaData = ref(null)
const schemaType = ref('')

let promiseResolve = null
let promiseReject = null

const formOptions = [
  {
    labelKey: 'api.label.generateEngine',
    prop: 'provider',
    type: 'select',
    children: [
      { label: 'openapi-sampler', value: 'openapi-sampler' },
      { label: 'Mock.js', value: 'mockjs' },
      { label: 'json-schema-faker', value: 'json-schema-faker' }
    ],
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
]

const showGenerateSampleWindow = (schemaBody, type) => {
  schemaData.value = schemaBody
  schemaType.value = type
  showDialog.value = true
  return new Promise((resolve, reject) => {
    promiseResolve = resolve
    promiseReject = reject
  })
}

const handleOk = () => {
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
