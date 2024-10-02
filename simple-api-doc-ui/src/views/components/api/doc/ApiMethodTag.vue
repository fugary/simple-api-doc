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
  return (props.method || 'get').toUpperCase()
})

const methodColors = {
  GET: 'success',
  POST: 'primary',
  PUT: 'warning',
  DELETE: 'danger',
  PATCH: 'info',
  HEAD: 'info',
  OPTIONS: 'info',
  TRACE: 'info'
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
