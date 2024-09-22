import { useResourceApi } from '@/hooks/ApiHooks'
import { ref, computed } from 'vue'
import { $http } from '@/vendors/axios'
import { isAdminUser, useCurrentUserName } from '@/utils'

const API_PROJECT_URL = '/admin/projects'
const ApiProjectApi = useResourceApi(API_PROJECT_URL)

export const useApiProjectItem = projectCode => {
  const projectItem = ref({})
  const loadSuccess = ref(false)

  loadByCode(projectCode).then(data => {
    projectItem.value = data
    loadSuccess.value = !!data
    console.log(projectItem.value)
  })

  const docUrl = computed(() => `/api-doc/${projectItem.value?.projectCode}`)

  return {
    projectItem,
    docUrl,
    loadSuccess
  }
}

/**
 * 加载当前用户可选项目
 * @return {Promise<T>}
 */
export const selectProjects = (data, config) => {
  return $http(Object.assign({
    url: `${API_PROJECT_URL}/selectProjects`,
    method: 'post',
    data
  }, config)).then(response => response.data?.resultData)
}

/**
 * 加载项目详情
 * @return {Promise<T>}
 */
export const loadByCode = (projectCode, config) => {
  return $http(Object.assign({
    url: `${API_PROJECT_URL}/loadByCode/${projectCode}`,
    method: 'get'
  }, config)).then(response => response.data?.resultData)
}

export const useSelectProjects = (searchParam) => {
  const projects = ref([])
  const projectOptions = ref([])
  const loadSelectProjects = (data, config) => {
    return selectProjects(data, config).then(result => {
      projects.value = result || []
      projectOptions.value = projects.value.map(project => ({ label: project.projectName, value: project.projectCode }))
    })
  }
  const loadProjectsAndRefreshOptions = async () => {
    await loadSelectProjects({
      userName: searchParam.value?.userName || useCurrentUserName()
    })
    const currentProj = projects.value.find(proj => proj.projectCode === searchParam.value.projectCode)
    searchParam.value.projectCode = currentProj?.projectCode
    if (isAdminUser() && currentProj?.userName) {
      searchParam.value.userName = currentProj.userName
    }
  }
  return {
    projects,
    projectOptions,
    loadSelectProjects,
    loadProjectsAndRefreshOptions
  }
}

export default ApiProjectApi
