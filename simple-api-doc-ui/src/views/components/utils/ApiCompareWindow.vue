<script setup lang="jsx">
import { computed, onBeforeUnmount, ref, shallowRef } from 'vue'
import { $i18nBundle } from '@/messages'
import { isFunction } from 'lodash-es'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { $copyText } from '@/utils'

const props = defineProps({
  title: {
    type: String,
    default: ''
  },
  original: {
    type: Object,
    default: undefined
  },
  modified: {
    type: Object,
    default: undefined
  },
  historyOptionsMethod: {
    type: Function,
    default: () => []
  },
  height: {
    type: [Number, String],
    default: '500px'
  }
})

const showWindow = defineModel({
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
    let propValue = isFunction(option.prop) ? option.prop(doc, history) : doc[option.prop]
    if (propValue === null || propValue === undefined) {
      propValue = ''
    }
    return `[${option.labelKey ? $i18nBundle(option.labelKey) : option.label}]
${propValue}`
  }).join('\n\n')
}

const fieldConfig = computed(() => {
  return {
    originalContent: calcHistoryContent(props.original, true),
    modifiedContent: calcHistoryContent(props.modified)
  }
})

const showApiCompareWindow = () => {
  showWindow.value = true
}

const copyOriginal = () => {
  $copyText({ text: fieldConfig.value.originalContent, success: $i18nBundle('common.msg.copySuccess') })
}

const copyModified = () => {
  $copyText({ text: fieldConfig.value.modifiedContent, success: $i18nBundle('common.msg.copySuccess') })
}

onBeforeUnmount(() => {
  diffEditorRef.value?.dispose()
})
defineExpose({
  showApiCompareWindow
})

const fullscreen = ref(false)
const codeHeight = computed(() => fullscreen.value ? 'calc(100dvh - 150px)' : props.height)

</script>

<template>
  <common-window
    v-model="showWindow"
    v-model:fullscreen="fullscreen"
    :title="title||$t('api.label.versionDiff')"
    width="1000px"
    show-fullscreen
    :show-cancel="false"
    :ok-label="$t('common.label.close')"
    :buttons="[
      { type: 'success', labelKey: 'common.label.copyOriginal', click: copyOriginal },
      { type: 'info', labelKey: 'common.label.copyModified', click: copyModified }
    ]"
  >
    <el-container class="flex-column">
      <vue-monaco-diff-editor
        v-if="original && modified"
        :theme="useGlobalConfigStore().monacoTheme"
        :original="fieldConfig.originalContent"
        :modified="fieldConfig.modifiedContent"
        language="markdown"
        :options="diffOptions"
        :height="codeHeight"
        @mount="handleMount"
      >
        <div v-loading="true" />
      </vue-monaco-diff-editor>
    </el-container>
  </common-window>
</template>

<style scoped>

</style>
