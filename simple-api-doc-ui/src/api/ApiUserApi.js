import { useResourceApi } from '@/hooks/ApiHooks'
import { useDefaultPage } from '@/config'
import { ref } from 'vue'
import { useCurrentUserName } from '@/utils'

const ApiUserApi = useResourceApi('/admin/users')

/**
 * @return {CommonAutocompleteOption}
 */
export const useUserAutocompleteConfig = () => {
  return {
    idProp: 'userName',
    labelProp: 'userName',
    columns: [{
      labelKey: 'common.label.username',
      property: 'userName'
    }, {
      labelKey: 'common.label.nickName',
      property: 'nickName'
    }, {
      labelKey: 'common.label.email',
      property: 'userEmail'
    }],
    searchMethod ({ query, page }, cb) {
      ApiUserApi.search({ status: 1, keyword: query, page })
        .then(result => {
          const data = {
            page: result.page,
            items: result.resultData
          }
          cb(data)
        })
    }
  }
}
/**
 * 加载所有可用用户，限制100个，一般不会有这么多
 * @return {Promise<T>}
 */
export const loadAllUsers = (config = {}) => {
  return ApiUserApi.search({ status: 1, page: useDefaultPage(100) }, config)
    .then(result => result.resultData)
}

export const useAllUsers = (searchParam) => {
  const users = ref([])
  const userOptions = ref([])
  const loadSelectUsers = () => {
    return loadAllUsers().then(result => {
      users.value = result || []
      userOptions.value = users.value.map(user => ({ label: user.userName, value: user.userName }))
    })
  }
  const loadUsersAndRefreshOptions = async (useCurrent = true) => {
    await loadSelectUsers()
    const userOpt = userOptions.value.find(option => option.value === searchParam.value.userName)
    searchParam.value.userName = userOpt?.value || (useCurrent ? useCurrentUserName() : undefined)
  }
  return {
    users,
    userOptions,
    loadSelectUsers,
    loadUsersAndRefreshOptions
  }
}

export default ApiUserApi
