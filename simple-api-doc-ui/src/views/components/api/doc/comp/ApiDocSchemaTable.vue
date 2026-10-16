<script setup>
import { ref, watch } from 'vue'
import { $copyText } from '@/utils'
import { MdPreview } from 'md-editor-v3'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { previewSchemaMarkdown } from '@/api/ApiProjectInfoDetailApi'

const props = defineProps({
  schema: { type: Object, default: () => ({}) },
  schemaName: { type: String, default: '' },
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
const handleCopyMarkdown = () => {
  if (loading.value || failed.value || !markdown.value) return
  $copyText(props.schemaName ? `# ${props.schemaName}\n\n${markdown.value}` : markdown.value)
}
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
    <div
      v-if="!loading && !failed && markdown"
      v-common-tooltip="$t('api.label.copyMarkdown')"
      class="floating-action-btn copy-md-btn"
      role="button"
      tabindex="0"
      :aria-label="$t('api.label.copyMarkdown')"
      @click="handleCopyMarkdown"
      @keydown.enter.prevent="handleCopyMarkdown"
      @keydown.space.prevent="handleCopyMarkdown"
    >
      <common-icon
        icon="CopyDocument"
        :size="16"
      />
    </div>
  </div>
</template>
