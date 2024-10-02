<script setup>
import { computed } from 'vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'

const currentDoc = defineModel({
  type: Object,
  default: () => ({})
})
const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')
</script>
<template>
  <md-editor
    v-model="currentDoc.docContent"
    :theme="theme"
    @update:model-value="$emit('change', $event)"
  />
</template>

<style scoped>

</style>
