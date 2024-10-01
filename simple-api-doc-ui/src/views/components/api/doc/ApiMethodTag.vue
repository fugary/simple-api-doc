<script setup>
import { computed } from 'vue'

const props = defineProps({
  method: {
    type: String,
    default: null
  },
  size: {
    type: String,
    default: 'small'
  }
})

const methodName = computed(() => {
  return (props.method || 'get').toLowerCase()
})

const methodColors = {
  get: 'success',
  post: 'primary',
  put: 'warning',
  delete: 'danger',
  patch: 'info',
  head: 'info',
  options: 'info',
  trace: 'info'
}

</script>

<template>
  <el-tag
    :type="methodColors[methodName]"
    :size="size"
  >
    {{ methodName }}
  </el-tag>
</template>

<style scoped>

</style>
