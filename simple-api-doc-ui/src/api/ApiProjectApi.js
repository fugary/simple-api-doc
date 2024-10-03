import { useResourceApi } from '@/hooks/ApiHooks'
import { ref } from 'vue'
import { $http, $httpPost } from '@/vendors/axios'
import { isAdminUser, useCurrentUserName } from '@/utils'
import { isArray } from 'lodash-es'

const API_PROJECT_URL = '/admin/projects'
const ApiProjectApi = useResourceApi(API_PROJECT_URL)

export const useApiProjectItem = (projectCode, autoLoad = true) => {
  const projectItem = ref()
  const loadSuccess = ref(false)
  const loading = ref(true)

  const loadProjectItem = (code) => {
    return loadByCode(code).then(data => {
      projectItem.value = data
      loadSuccess.value = !!data
      console.log(projectItem.value)
    }).finally(() => (loading.value = false))
  }
  if (autoLoad) {
    loadProjectItem(projectCode)
  }

  return {
    projectItem,
    loadProjectItem,
    loading,
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
/**
 * 导入项目
 * @param files 文件列表
 * @param params 其他参数
 * @param config 配置项
 * @return {Promise<*>}
 */
export const importProject = (files, params = {}, config = {}) => {
  const formData = new FormData()
  files = isArray(files) ? files : [files]
  files.filter(file => file.raw).forEach(file => formData.append('files', file.raw))
  for (const key in params) {
    params[key] && formData.append(key, params[key])
  }
  const url = params.projectId ? `${API_PROJECT_URL}/importExistsProject` : `${API_PROJECT_URL}/importProject`
  return $httpPost(url,
    formData, Object.assign({ headers: { 'Content-Type': 'multipart/form-data' }, loading: true }, config))
}

export const generateJWT = function (data, config) {
  return $http(Object.assign({
    url: `${API_PROJECT_URL}/generateJwt`,
    method: 'post',
    data
  }, config)).then(response => response.data)
}

export default ApiProjectApi
