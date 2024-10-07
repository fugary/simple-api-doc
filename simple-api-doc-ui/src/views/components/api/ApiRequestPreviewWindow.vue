<script setup>
import { ref, nextTick } from 'vue'
import ApiDocRequestPreview from '@/views/components/api/ApiDocRequestPreview.vue'
import emitter from '@/vendors/emitter'

const showWindow = ref(false)
const loading = ref(true)
const mockPreviewRef = ref()
const toPreviewRequest = async (...args) => {
  showWindow.value = true
  nextTick(() => {
    mockPreviewRef.value?.toPreviewRequest(...args)
      .finally(() => { loading.value = false })
  })
}

emitter.on('preview-401-error', () => (showWindow.value = false))

defineExpose({
  toPreviewRequest
})

</script>

<template>
  <common-window
    v-model="showWindow"
    width="1100px"
    :show-cancel="false"
    :close-on-click-modal="false"
    :ok-label="$t('common.label.close')"
    show-fullscreen
    destroy-on-close
  >
    <template #header>
      <span class="el-dialog__title">
        {{ $t('api.msg.requestTest') }}
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
