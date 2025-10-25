<script setup>
import { ref, nextTick, onUnmounted } from 'vue'
import ApiDocRequestPreview from '@/views/components/api/ApiDocRequestPreview.vue'
import emitter from '@/vendors/emitter'
import { useMediaQuery } from '@vueuse/core'

const showWindow = ref(false)
const loading = ref(true)
const mockPreviewRef = ref()
const currentDoc = ref()
const changeForceShowWindow = ref()
const toPreviewRequest = async (projectInfo, apiDoc, changeForceShowWindowFunc, ...args) => {
  currentDoc.value = apiDoc
  changeForceShowWindow.value = () => {
    changeForceShowWindowFunc?.()
    showWindow.value = false
  }
  showWindow.value = true
  nextTick(() => {
    mockPreviewRef.value?.toPreviewRequest(projectInfo, apiDoc, ...args)
      .finally(() => { loading.value = false })
  })
}

const errorHandler = () => (showWindow.value = false)

emitter.on('preview-401-error', errorHandler)
onUnmounted(() => {
  emitter.off('preview-401-error', errorHandler)
})

defineExpose({
  toPreviewRequest
})
const verySmallWindow = useMediaQuery('(max-width: 1000px)')
</script>

<template>
  <common-window
    v-model="showWindow"
    width="1024px"
    :show-cancel="false"
    :close-on-click-modal="false"
    :ok-label="$t('common.label.close')"
    show-fullscreen
    destroy-on-close
  >
    <template #header>
      <span class="el-dialog__title">
        {{ currentDoc?.docName || currentDoc?.url }}
        <el-link
          v-if="!verySmallWindow"
          v-common-tooltip="$t('api.label.debugInFitScreen')"
          type="primary"
          underline="never"
          class="margin-left1"
          @click="changeForceShowWindow"
        >
          <common-icon
            size="18"
            style="transform: rotate(90deg);"
            icon="OpenInNewFilled"
          />
        </el-link>
      </span>
    </template>
    <api-doc-request-preview
      ref="mockPreviewRef"
      v-loading="loading"
      style="min-height:200px;"
    />
  </common-window>
</template>

<style scoped>

</style>
