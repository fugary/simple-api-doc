<script setup>
import { computed, reactive, ref, shallowRef } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { buildCodeRequest } from '@/services/api/CurlProcessService'
import { generateRequestCode, REQUEST_CODE_LANGUAGES } from '@/services/api/RequestCodeService'
import { useMonacoEditorOptions } from '@/vendors/monaco-editor'
import { $copyText } from '@/utils'

defineProps({ monacoTheme: { type: String, default: 'vs' } })
const visible = ref(false)
const fullscreen = ref(false)
const loading = ref(false)
const error = ref(false)
const request = shallowRef()
const selectedLanguage = useLocalStorage('api-request-code-language', 'curl')
const language = computed(() => REQUEST_CODE_LANGUAGES.find(item => item.value === selectedLanguage.value) || REQUEST_CODE_LANGUAGES[0])
const model = reactive({ language: selectedLanguage })
const languageOption = {
  labelKey: 'api.label.requestCodeLanguage',
  prop: 'language',
  type: 'select',
  children: REQUEST_CODE_LANGUAGES,
  attrs: { clearable: false, style: { width: '250px' } }
}
const code = computed(() => generateRequestCode(request.value, language.value.value))
const { monacoEditorOptions } = useMonacoEditorOptions({ readOnly: true, automaticLayout: true })
let generation = 0
const open = async (createTarget) => {
  const current = ++generation
  visible.value = true
  request.value = undefined
  error.value = false
  loading.value = true
  try {
    const target = await createTarget()
    const result = await buildCodeRequest(target, target.requestPath)
    if (current === generation) request.value = result
  } catch {
    if (current === generation) error.value = true
  } finally {
    if (current === generation) loading.value = false
  }
}
const copyCode = () => {
  if (code.value) $copyText(code.value)
  return false
}
defineExpose({ open })
</script>

<template>
  <common-window
    v-model="visible"
    v-model:fullscreen="fullscreen"
    :title="$t('api.label.requestCode')"
    width="900px"
    append-to-body
    destroy-on-close
    show-fullscreen
    :show-ok="!!code && !loading"
    :ok-label="$t('api.label.copyRequestCode')"
    :ok-click="copyCode"
    :cancel-label="$t('common.label.close')"
  >
    <div
      v-loading="loading"
      class="request-code-content"
    >
      <common-form-control
        :model="model"
        :option="languageOption"
      />
      <el-alert
        class="margin-bottom2"
        :title="$t(error ? 'api.msg.requestCodeError' : 'api.msg.requestCodeDocument')"
        :type="error ? 'error' : 'info'"
        :closable="false"
      />
      <vue-monaco-editor
        :value="code"
        :language="language.language"
        :theme="monacoTheme"
        :options="monacoEditorOptions"
        :height="fullscreen ? 'calc(100dvh - 260px)' : '450px'"
      />
    </div>
  </common-window>
</template>

<style scoped>
.request-code-content {
  width: 100%;
  min-width: 0;
}
</style>
