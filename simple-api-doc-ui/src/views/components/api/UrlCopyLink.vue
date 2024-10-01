<script setup>
import { $copyText, getPathUrl } from '@/utils'

const props = defineProps({
  urlPath: {
    type: String,
    default: ''
  },
  content: {
    type: String,
    default: ''
  },
  tooltip: {
    type: String,
    default: '复制链接地址'
  },
  icon: {
    type: String,
    default: 'DocumentCopy'
  }
})

const copyInfo = () => {
  let info = props.content
  if (!info && props.urlPath) {
    info = getPathUrl(props.urlPath)
  }
  if (info) {
    $copyText(info)
  }
}

</script>

<template>
  <el-link
    v-common-tooltip="tooltip"
    type="primary"
    :underline="false"
    v-bind="$attrs"
    @click="copyInfo"
  >
    <common-icon
      :size="18"
      :icon="icon"
    />
  </el-link>
</template>

<style scoped>

</style>
