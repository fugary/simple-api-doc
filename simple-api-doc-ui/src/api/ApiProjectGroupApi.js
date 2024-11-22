import { useResourceApi } from '@/hooks/ApiHooks'
import { $http, $httpPost } from '@/vendors/axios'
import { ref } from 'vue'
import { isAdminUser, useCurrentUserName } from '@/utils'

const BASE_URL = '/admin/groups'

const ApiProjectGroupApi = useResourceApi(BASE_URL)

/**
 * 加载当前用户可选项目分组
 * @return {Promise<T>}
 */
export const loadProjectGroups = (params, config) => {
  return $http(Object.assign({
    url: `${BASE_URL}/loadProjectGroups`,
    method: 'GET',
    params
  }, config)).then(response => response.data?.resultData)
}

/**
 * 加载分组成员
 * @param groupCode
 * @param config
 * @return {Promise<T>}
 */
export const loadGroupUsers = (groupCode, config) => {
  return $httpPost(`${BASE_URL}/loadGroupUsers/${groupCode}`, null, config)
}

/**
 * 保存分组成员
 * @param userGroupVo
 * @param [config]
 * @return {Promise<T>}
 */
export const saveUserGroups = (userGroupVo, config) => {
  return $httpPost(`${BASE_URL}/saveUserGroups`, userGroupVo, config)
}

export const useSelectProjectGroups = (searchParam) => {
  const projectGroups = ref([])
  const projectGroupOptions = ref([])
  const loadSelectGroups = (data, config) => {
    return loadProjectGroups(data, config).then(result => {
      projectGroups.value = result || []
      projectGroupOptions.value = projectGroups.value.map(group => ({ label: group.groupName, value: group.groupCode }))
    })
  }
  const loadGroupsAndRefreshOptions = async () => {
    await loadSelectGroups({
      userName: searchParam.value?.userName || useCurrentUserName()
    })
    const currentGroup = projectGroups.value.find(group => group.groupCode === searchParam.value.groupCode)
    searchParam.value.projectCode = currentGroup?.projectCode
    if (isAdminUser() && currentGroup?.userName) {
      searchParam.value.userName = currentGroup.userName
    }
  }

  return {
    projectGroups,
    projectGroupOptions,
    loadSelectGroups,
    loadGroupsAndRefreshOptions
  }
}

export default ApiProjectGroupApi
