<script setup>
import { computed } from 'vue'
import { escapeRegExp } from 'lodash-es'

const props = defineProps({
  text: { type: String, default: '' },
  keyword: { type: String, default: '' }
})
// 按字面拆分成文本节点，避免把接口描述或 Markdown 中的 HTML 当作标签执行。
const parts = computed(() => {
  const text = props.text || ''
  return props.keyword ? text.split(new RegExp(`(${escapeRegExp(props.keyword)})`, 'gi')) : [text]
})
</script>

<template>
  <span><template
    v-for="(part, index) in parts"
    :key="index"
  ><mark v-if="index % 2">{{ part }}</mark><template v-else>{{ part }}</template></template></span>
</template>

<style scoped>
mark {
  background: var(--el-color-warning-light-7);
  color: var(--el-text-color-primary);
  border-radius: 2px;
}
</style>
