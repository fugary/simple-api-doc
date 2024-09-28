<script setup>
import { computed } from 'vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'

const vModel = defineModel({
  type: String,
  default: ''
})
const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')
defineEmits(['change'])
</script>
<template>
  <md-editor
    v-model="vModel"
    :theme="theme"
    @update:model-value="$emit('change', $event)"
  />
</template>

<style scoped>

</style>
