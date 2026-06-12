import { computed, ref } from 'vue'
import { defineFormOptions } from '@/components/utils'
import { useFormStatus } from '@/consts/GlobalConstants'
import { isAdminUser, useCurrentUserName } from '@/utils'
import ApiProjectGroupApi from '@/api/ApiProjectGroupApi'
import { $i18nBundle } from '@/messages'
import { AUTHORITY_TYPE } from '@/consts/ApiConstants'

const AUTHORITY_ITEMS = {
  [AUTHORITY_TYPE.READABLE]: ['R', 'api.label.authority_readable'],
  [AUTHORITY_TYPE.WRITABLE]: ['W', 'api.label.authority_writable'],
  [AUTHORITY_TYPE.DELETABLE]: ['D', 'api.label.authority_deletable']
}
const authValues = Object.keys(AUTHORITY_ITEMS)

export const parseAuthorities = authorities => (authorities || '').split(/\s*,\s*/)
  .filter(authority => authValues.includes(authority))
  .sort((key1, key2) => authValues.indexOf(key1) - authValues.indexOf(key2))

export const getAuthorityCode = authorities => authorities.map(authority => AUTHORITY_ITEMS[authority][0]).join('')

export const getAuthorityTooltip = authorities => authorities.map(authority => {
  const item = AUTHORITY_ITEMS[authority]
  return `${item[0]}: ${$i18nBundle(item[1])}`
}).join('<br/>')

export const getGroupUserAuthorities = (userGroups, apiUsers = []) => {
  const apiUserMap = Object.fromEntries(apiUsers.map(apiUser => [apiUser.id, apiUser]))
  return (userGroups || []).map(userGroup => ({
    apiUser: apiUserMap[userGroup.userId],
    authorities: parseAuthorities(userGroup.authorities)
  })).filter(({ apiUser, authorities }) => apiUser && authorities.length)
}

export const useProjectGroupEditHook = (formModel, userOptions) => {
  const showEditWindow = ref(false)
  const currentGroup = ref()
  const newOrEditGroup = async (id, $event) => {
    $event?.stopPropagation()
    if (id) {
      await ApiProjectGroupApi.getById(id).then(data => {
        data.resultData && (currentGroup.value = data.resultData)
      })
    } else {
      currentGroup.value = {
        status: 1,
        userName: formModel.value?.userName || useCurrentUserName()
      }
    }
    showEditWindow.value = true
  }
  const editFormOptions = computed(() => defineFormOptions([{
    labelKey: 'common.label.user',
    prop: 'userName',
    type: 'select',
    enabled: isAdminUser(),
    children: userOptions.value,
    attrs: {
      clearable: false
    }
  }, {
    labelKey: 'api.label.projectGroupName',
    prop: 'groupName',
    required: true
  }, useFormStatus(), {
    labelKey: 'common.label.description',
    prop: 'description',
    attrs: {
      type: 'textarea'
    }
  }]))
  return {
    currentGroup,
    newOrEditGroup,
    showEditWindow,
    editFormOptions
  }
}
