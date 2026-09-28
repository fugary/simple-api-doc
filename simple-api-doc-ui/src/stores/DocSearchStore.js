import { defineStore } from 'pinia'
import { ref } from 'vue'

// 仅在当前会话记忆搜索，不将正文摘要写入本地持久化缓存。
export const useDocSearchStore = defineStore('docSearch', () => {
  const sessions = ref({})
  return { sessions }
})
