import { useResourceApi } from '@/hooks/ApiHooks'
import { $http, $httpPost } from '@/vendors/axios'
import { ref, h } from 'vue'
import { useCurrentUserName, isAdminUser } from '@/utils'
import { checkCurrentAuthAccess } from '@/services/api/ApiCommonService'
import { ElText } from 'element-plus'

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

/**
 * 格式化分组名称与用户高亮标签
 * @param {Object} group
 * @return {import('vue').VNode|string}
 */
export const renderProjectGroupLabel = (group) => {
  if (!group) {
    return ''
  }
  return h('span', [
    group.groupName,
    group.userName ? h(ElText, { type: 'success', tag: 'b', class: 'margin-left1' }, `(${group.userName})`) : null
  ])
}

export const useSelectProjectGroups = (searchParam) => {
  const projectGroups = ref([])
  const projectGroupOptions = ref([])
  const loadSelectGroups = (data, config) => {
    return loadProjectGroups(data, config).then(result => {
      projectGroups.value = result || []
      const currentUserName = useCurrentUserName()
      projectGroups.value.sort((a, b) => {
        const isSelfA = a.userName === currentUserName ? 0 : 1
        const isSelfB = b.userName === currentUserName ? 0 : 1
        if (isSelfA !== isSelfB) {
          return isSelfA - isSelfB
        }
        const userCompare = (a.userName || '').localeCompare(b.userName || '')
        if (userCompare !== 0) {
          return userCompare
        }
        return (a.id || 0) - (b.id || 0)
      })
      projectGroupOptions.value = projectGroups.value.map(group => {
        const label = group.userName ? `${group.groupName} (${group.userName})` : group.groupName
        return {
          id: group.id,
          label,
          value: group.groupCode,
          userName: group.userName,
          status: group.status,
          groupName: group.groupName,
          slots: {
            default: () => renderProjectGroupLabel(group)
          }
        }
      })
    })
  }
  const loadGroupsAndRefreshOptions = async () => {
    const userName = (isAdminUser() && searchParam.value?.userName) ? searchParam.value.userName : useCurrentUserName()
    await loadSelectGroups({ userName })
    const currentGroup = projectGroups.value.find(group => group.groupCode === searchParam.value.groupCode)
    searchParam.value.projectCode = currentGroup?.projectCode
  }

  const projectCheckAccess = (groupCode, authority) => {
    const currentGroup = projectGroups.value?.find(group => group.groupCode === groupCode)
    const currentUserName = useCurrentUserName()
    if (currentUserName === currentGroup?.userName) {
      return true
    }
    const authorities = currentGroup?.authorities
    return checkCurrentAuthAccess({
      userName: useCurrentUserName(),
      groupCode,
      authorities
    }, authority)
  }

  return {
    projectGroups,
    projectGroupOptions,
    loadSelectGroups,
    loadGroupsAndRefreshOptions,
    projectCheckAccess
  }
}

export const inProjectCheckAccess = (projectItem, authority) => {
  if (projectItem) {
    const { apiGroup, userName, groupCode } = projectItem
    if (apiGroup?.userName === useCurrentUserName()) {
      return true
    }
    const authorities = apiGroup?.authorities
    return checkCurrentAuthAccess({ userName, groupCode, authorities }, authority)
  }
}

export default ApiProjectGroupApi
