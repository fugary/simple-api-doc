<script setup>
import { computed } from 'vue'
import { ALL_METHODS } from '@/consts/ApiConstants'

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

const methodColors = Object.fromEntries(ALL_METHODS.map(method => [method.method, method.type]))

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
