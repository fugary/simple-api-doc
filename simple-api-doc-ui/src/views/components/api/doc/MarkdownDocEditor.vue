<script setup>
import Vditor from 'vditor'
import 'vditor/dist/index.css'

import { shallowRef, watch, onMounted } from 'vue'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'

const globalConfigStore = useGlobalConfigStore()

const props = defineProps({
  content: {
    type: String,
    default: ''
  }
})

const getTheme = () => globalConfigStore.isDarkTheme ? 'dark' : 'light'

const vditorConfig = {
  preview: {
    theme: getTheme()
  },
  outline: {
    enabled: true,
    position: 'right'
  }
}

const vditor = shallowRef()
onMounted(() => {
  vditor.value = new Vditor('markdown-container', {
    ...vditorConfig,
    value: props.content
  })
})
watch(() => props.content, content => {
  vditor.value?.setValue(content)
})
watch(() => globalConfigStore.isDarkTheme, () => {
  vditor.value?.setTheme(getTheme())
})
</script>
<template>
  <div id="markdown-container" />
</template>

<style scoped>

</style>
