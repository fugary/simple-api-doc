<script setup>
import { ref, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { previewSchemaMarkdown } from '@/api/ApiProjectInfoDetailApi'

const props = defineProps({
  schema: { type: Object, default: () => ({}) },
  componentSchemas: { type: Array, default: () => [] },
  specVersion: { type: String, default: undefined }
})
const globalConfig = useGlobalConfigStore()
const markdown = ref('')
const loading = ref(false)
const failed = ref(false)
watch(() => [props.schema, props.componentSchemas, props.specVersion, globalConfig.currentLocale], async (_, __, onCleanup) => {
  let cancelled = false
  onCleanup(() => { cancelled = true })
  markdown.value = ''
  loading.value = true
  failed.value = false
  try {
    const result = await previewSchemaMarkdown({
      schemaContent: JSON.stringify(props.schema),
      componentSchemas: props.componentSchemas.map(({ schemaName, schemaContent }) => ({ schemaName, schemaContent })),
      specVersion: props.specVersion
    })
    if (!cancelled) {
      markdown.value = result.resultData || ''
      failed.value = !result.success
    }
  } catch {
    if (!cancelled) failed.value = true
  } finally {
    if (!cancelled) loading.value = false
  }
}, { immediate: true, deep: true })
</script>

<template>
  <div v-loading="loading">
    <el-alert
      v-if="failed"
      type="error"
      :title="$t('common.msg.operationFailed')"
      :closable="false"
    />
    <md-preview
      v-else
      class="md-doc-container"
      :theme="globalConfig.isDarkTheme ? 'dark' : 'light'"
      :model-value="markdown"
    />
  </div>
</template>
