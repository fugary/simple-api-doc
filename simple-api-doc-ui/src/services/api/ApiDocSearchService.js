import { computed, onUnmounted, ref } from 'vue'
import { debounce } from 'lodash-es'
import { searchDocProjects } from '@/api/ApiDocSearchApi'

export const useDocProjectSelector = (getProjectId, projects) => {
  const loading = ref(false)
  let requestVersion = 0
  const search = debounce(async (keyword, version) => {
    try {
      const data = await searchDocProjects(keyword)
      if (version === requestVersion) {
        const selected = projects.value.find(project => project.id === getProjectId())
        projects.value = selected && !data.some(project => project.id === selected.id) ? [selected, ...data] : data
      }
    } catch {
      // 请求层统一显示错误。
    } finally {
      if (version === requestVersion) loading.value = false
    }
  }, 250)
  const loadProjects = (keyword = '') => {
    loading.value = true
    search(keyword, ++requestVersion)
  }
  onUnmounted(() => {
    requestVersion++
    search.cancel()
  })
  const projectSelectAttrs = computed(() => ({
    filterable: true,
    remote: true,
    remoteShowSuffix: true,
    remoteMethod: loadProjects,
    loading: loading.value
  }))
  return { loadProjects, projectSelectAttrs }
}
