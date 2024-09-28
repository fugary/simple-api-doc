<script setup>
import { Marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js'
import 'highlight.js/styles/monokai.css'
import { computed } from 'vue'

const props = defineProps({
  content: {
    type: String,
    default: ''
  }
})

const html = computed(() => {
  const marked = new Marked(
    markedHighlight({
      langPrefix: 'hljs language-',
      highlight (code, lang) {
        console.log('======================lang', lang, code)
        const language = hljs.getLanguage(lang) ? lang : 'plaintext'
        return hljs.highlight(code, { language }).value
      }
    })
  )
  return marked.parse(props.content)
})

</script>

<template>
  <el-container class="padding-left2 padding-right2">
    <div v-html="html" />
  </el-container>
</template>

<style scoped>

</style>
