<script setup lang="jsx">
import { computed, shallowRef } from 'vue'
import { $i18nBundle } from '@/messages'
import { isFunction } from 'lodash-es'

const props = defineProps({
  originalDoc: {
    type: Object,
    default: undefined
  },
  modifiedDoc: {
    type: Object,
    default: undefined
  },
  historyOptionsMethod: {
    type: Function,
    default: () => []
  }
})

const showDiffViewer = defineModel({
  type: Boolean,
  default: false
})

const diffOptions = {
  automaticLayout: true,
  formatOnType: true,
  formatOnPaste: true,
  readOnly: true
}
const diffEditorRef = shallowRef()
const handleMount = diffEditor => (diffEditorRef.value = diffEditor)

const calcHistoryContent = (doc, history) => {
  const options = props.historyOptionsMethod(doc, history).filter(item => item.enabled !== false)
  return options.map(option => {
    const propValue = isFunction(option.prop) ? option.prop(doc, history) : doc[option.prop]
    return `[${option.labelKey ? $i18nBundle(option.labelKey) : option.label}]
${propValue ?? ''}`
  }).join('\n\n')
}

const fieldConfig = computed(() => {
  return {
    originalContent: calcHistoryContent(props.originalDoc, true),
    modifiedContent: calcHistoryContent(props.modifiedDoc)
  }
})

</script>

<template>
  <common-window
    v-model="showDiffViewer"
    :title="$t('api.label.versionDiff')"
    width="1000px"
    show-fullscreen
    :show-cancel="false"
    :ok-label="$t('common.label.close')"
  >
    <el-container class="flex-column">
      <vue-monaco-diff-editor
        v-if="originalDoc && modifiedDoc"
        theme="vs-dark"
        :original="fieldConfig.originalContent"
        :modified="fieldConfig.modifiedContent"
        language="markdown"
        :options="diffOptions"
        style="height:500px;"
        @mount="handleMount"
      >
        <div v-loading="true" />
      </vue-monaco-diff-editor>
    </el-container>
  </common-window>
</template>

<style scoped>

</style>
