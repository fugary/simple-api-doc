import { useResourceApi } from '@/hooks/ApiHooks'
import { ref } from 'vue'
import { $http, $httpPost } from '@/vendors/axios'
import { $downloadWithLinkClick, isAdminUser, useCurrentUserName } from '@/utils'
import { isArray, isFunction } from 'lodash-es'
import { BASE_URL } from '@/config'
import { useLoginConfigStore } from '@/stores/LoginConfigStore'

const API_PROJECT_URL = '/admin/projects'
const ApiProjectApi = useResourceApi(API_PROJECT_URL)

export const useApiProjectItem = (projectCode, config = {}) => {
  const projectItem = ref()
  const loadSuccess = ref(false)
  const loading = ref(false)
  const { autoLoad, detail } = Object.assign({ autoLoad: true, detail: true }, config)
  const loadProjectItem = (code) => {
    loading.value = true
    return (detail ? loadDetail : loadBasic)(code).then(data => {
      projectItem.value = data
      loadSuccess.value = !!data
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
export const selectProjects = (params, config) => {
  return $http(Object.assign({
    url: `${API_PROJECT_URL}/selectProjects`,
    method: 'GET',
    params
  }, config)).then(response => response.data?.resultData)
}

/**
 * 加载项目详情
 * @return {Promise<T>}
 */
export const loadDetail = (projectCode, config) => {
  return $http(Object.assign({
    url: `${API_PROJECT_URL}/loadDetail/${projectCode}`,
    method: 'get'
  }, config)).then(response => response.data?.resultData)
}

/**
 * 加载项目基本信息
 * @return {Promise<T>}
 */
export const loadBasic = (projectCode, config) => {
  return $http(Object.assign({
    url: `${API_PROJECT_URL}/loadBasic/${projectCode}`,
    method: 'get'
  }, config)).then(response => response.data?.resultData)
}

/**
 * 加载项目详情
 * @return {Promise<T>}
 */
export const loadDetailById = (projectId, config) => {
  return $http(Object.assign({
    url: `${API_PROJECT_URL}/loadDetailById/${projectId}`,
    method: 'get'
  }, config)).then(response => response.data?.resultData)
}

/**
 * 加载项目基本信息
 * @return {Promise<T>}
 */
export const loadBasicById = (projectId, config) => {
  return $http(Object.assign({
    url: `${API_PROJECT_URL}/loadBasicById/${projectId}`,
    method: 'get'
  }, config)).then(response => response.data?.resultData)
}
/**
 * 分组复制
 * @param id
 * @param [config]
 * @returns {Promise<axios.AxiosResponse<any>>}
 */
export const copyProject = (id, config) => {
  return $http(Object.assign({
    url: `${API_PROJECT_URL}/copy/${id}`,
    method: 'POST'
  }, config)).then(response => response.data?.resultData)
}

/**
 * 保存环境配置
 * @param id
 * @param data
 * @param [config]
 * @returns {Promise<axios.AxiosResponse<any>>}
 */
export const saveEnvConfigs = (id, data, config) => {
  return $http(Object.assign({
    url: `${API_PROJECT_URL}/saveEnvConfigs/${id}`,
    method: 'POST',
    data
  }, config)).then(response => response.data?.resultData)
}

export const useSelectProjects = (searchParam) => {
  const projects = ref([])
  const projectOptions = ref([])
  const loadSelectProjects = (data, config) => {
    return selectProjects(data, config).then(result => {
      projects.value = result || []
      projectOptions.value = projects.value.map(project => ({ label: project.projectName, value: project.id, groupCode: project.groupCode, projectCode: project.projectCode, userName: project.userName }))
    })
  }
  const loadProjectsAndRefreshOptions = async () => {
    await loadSelectProjects({
      userName: isAdminUser() ? searchParam.value?.userName : useCurrentUserName(),
      groupCode: searchParam.value?.groupCode
    })
    const currentProj = projects.value.find(proj => searchParam.value.projectId
      ? proj.id === searchParam.value.projectId
      : (searchParam.value.projectCode && proj.projectCode === searchParam.value.projectCode)
    )
    searchParam.value.projectCode = currentProj?.projectCode
    searchParam.value.projectId = currentProj?.id
  }

  return {
    projects,
    projectOptions,
    loadSelectProjects,
    loadProjectsAndRefreshOptions
  }
}
/**
 * 文件上传
 * @param files
 * @param [callback]
 * @param [projectCode]
 */
export const uploadFiles = (files, callback, projectCode) => {
  const formData = new FormData()
  files = isArray(files) ? files : [files]
  files.forEach(file => formData.append('files', file))
  if (projectCode) {
    formData.append('projectCode', projectCode)
  }
  return $httpPost('/upload/uploadFiles', formData,
    Object.assign({ headers: { 'Content-Type': 'multipart/form-data' }, loading: true, timeout: 60000 }))
    .then(data => {
      data.success && isFunction(callback) && callback(data.resultData)
    })
}
/**
 * 计算ProjectIconUrl
 * @param iconUrl
 * @returns {*}
 */
export const calcProjectIconUrl = (iconUrl) => {
  if (iconUrl && !iconUrl.match(/https?:\/\/.*/)) {
    iconUrl = BASE_URL + iconUrl
  }
  return iconUrl
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
    formData, Object.assign({ headers: { 'Content-Type': 'multipart/form-data' }, loading: true, timeout: 60000 }, config))
}

/**
 * 快速嗅探文件或文本的数据格式类型
 * @param {File|Blob|string} fileOrText 上传文件或字符串
 * @returns {Promise<string|null>} 'openapi' | 'markdown' | null
 */
export const detectImportFileType = async (fileOrText) => {
  if (!fileOrText) {
    return null
  }
  const fileName = (fileOrText?.name || fileOrText?.raw?.name || '').toLowerCase()
  if (fileName.endsWith('.zip') || fileName.endsWith('.md') || fileName.endsWith('.markdown')) {
    return 'markdown'
  }
  let text = ''
  if (typeof fileOrText === 'string') {
    text = fileOrText.length > 64 * 1024 ? fileOrText.slice(0, 64 * 1024) : fileOrText
  } else if (fileOrText.slice && typeof fileOrText.slice === 'function') {
    const sliceBlob = fileOrText.slice(0, 64 * 1024)
    if (typeof sliceBlob.text === 'function') {
      text = await sliceBlob.text()
    } else {
      text = await new Promise((resolve) => {
        const reader = new FileReader()
        reader.onload = () => resolve(reader.result || '')
        reader.onerror = () => resolve('')
        reader.readAsText(sliceBlob)
      })
    }
  }
  const trimmed = (text || '').trim()
  if (!trimmed) {
    return null
  }

  // 1. JSON 对象格式：包含 openapi / swagger，或同时包含 paths 与 info
  if (trimmed.startsWith('{')) {
    if (trimmed.includes('"openapi"') || trimmed.includes('"swagger"') ||
       (trimmed.includes('"paths"') && trimmed.includes('"info"'))) {
      return 'openapi'
    }
  }

  // 2. Swagger / OpenAPI (YAML 格式)
  if (/^openapi\s*:\s*['"]?3\./m.test(trimmed) ||
      /^swagger\s*:\s*['"]?2\./m.test(trimmed) ||
      (/^\s*['"]?(openapi|swagger)['"]?\s*:/m.test(trimmed)) ||
      (/^paths\s*:/m.test(trimmed) && /^info\s*:/m.test(trimmed))) {
    return 'openapi'
  }

  // 3. 排除 JSON 对象后，其余非 OpenAPI YAML 的文本均判定为 markdown
  if (!trimmed.startsWith('{')) {
    return 'markdown'
  }

  return null
}

export const generateJWT = function (data, config) {
  return $http(Object.assign({
    url: `${API_PROJECT_URL}/generateJwt`,
    method: 'post',
    data
  }, Object.assign({ addToken: false }, config))).then(response => response.data)
}

export const xml2Json = function (data, config) {
  return $http(Object.assign({
    url: `${API_PROJECT_URL}/xml2Json`,
    method: 'post',
    data
  }, Object.assign({ addToken: false }, config))).then(response => response.data)
}

export const checkExportProjectDocs = function (param, config) {
  return $httpPost(`${API_PROJECT_URL}/checkExportDownloadDocs`, param, Object.assign({ loading: true, timeout: 60000 }, config))
}

export const downloadExportProjectDocs = function ({ type, projectCode, uuid }) {
  const accessToken = useLoginConfigStore().accessToken
  const downloadUrl = `${BASE_URL}${API_PROJECT_URL}/exportDownload/${type}/${projectCode}/${uuid}?access_token=${accessToken}`
  $downloadWithLinkClick(downloadUrl)
}

export default ApiProjectApi
