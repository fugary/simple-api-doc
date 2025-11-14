<script setup>
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { computed, ref } from 'vue'
import { uploadFiles } from '@/api/ApiProjectApi'
import { $i18nKey } from '@/messages'
import { isPlainObject } from 'lodash-es'

const vModel = defineModel({
  type: String,
  default: ''
})
const showWindow = ref(false)
const fullscreen = ref(false)
const contentConfig = ref({})
const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')
const editorHeight = computed(() => fullscreen.value ? 'calc(100dvh - 145px)' : '500px')
const showMarkdownWindow = (content) => {
  if (isPlainObject(content)) {
    Object.assign(contentConfig.value, content)
    content = content.content
  }
  vModel.value = content
  showWindow.value = true
}

defineExpose({
  showMarkdownWindow
})

</script>

<template>
  <common-window
    v-model="showWindow"
    v-model:fullscreen="fullscreen"
    width="1100px"
    :title="contentConfig.title||$i18nKey('common.label.commonEdit', 'api.label.mdDocument')"
    :show-cancel="false"
    destroy-on-close
    append-to-body
    show-fullscreen
    :close-on-click-modal="false"
    :ok-label="$t('common.label.close')"
  >
    <md-editor
      v-model="vModel"
      :style="{height: editorHeight}"
      :theme="theme"
      class="scroll-main-container"
      :on-upload-img="uploadFiles"
    />
  </common-window>
</template>

<style scoped>

</style>
