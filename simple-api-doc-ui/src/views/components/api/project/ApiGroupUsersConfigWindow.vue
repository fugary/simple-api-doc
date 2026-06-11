<script setup lang="jsx">
import { computed, ref } from 'vue'
import { useAllUsers } from '@/api/ApiUserApi'
import { loadGroupUsers, saveUserGroups } from '@/api/ApiProjectGroupApi'
import { defineFormOptions, defineTableButtons, defineTableColumns } from '@/components/utils'
import { ALL_AUTHORITIES, AUTHORITY_TYPE, AUTHORITY_TYPE_MAPPING } from '@/consts/ApiConstants'
import { omit } from 'lodash-es'
import { $coreConfirm, isUserAdmin } from '@/utils'
import { $i18nBundle } from '@/messages'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import { ElTag } from 'element-plus'

const { users, loadSelectUsers } = useAllUsers()
const CONFIG_AUTHORITIES = ALL_AUTHORITIES.filter(item => item.value !== AUTHORITY_TYPE.FORBIDDEN)

const showWindow = defineModel({
  type: Boolean,
  default: false
})

const configModel = ref({
  groupCode: '',
  userGroups: []
})

const groupItem = ref()
const currentUserGroup = ref()
const showEditWindow = ref(false)

const parseAuthorities = auths => (Array.isArray(auths) ? auths : (auths || '').split(','))
  .filter(authority => authority && authority !== AUTHORITY_TYPE.FORBIDDEN)

const findUser = userId => users.value.find(user => user.id === userId)

const selectableUserOptions = (item = {}) => {
  const selectedUserIds = new Set(configModel.value.userGroups.map(userGroup => userGroup.userId).filter(Boolean))
  item.userId && selectedUserIds.delete(item.userId)
  return users.value
    .filter(user => !selectedUserIds.has(user.id) && !isUserAdmin(user.userName) && user.userName !== groupItem.value?.userName)
    .map(user => ({ label: user.userName, value: user.id }))
}

const processGroupUsers = (groupCode, data) => {
  groupItem.value = data.addons?.group
  return (data.resultData || []).map(groupUser => ({
    ...groupUser,
    groupCode,
    user: findUser(groupUser.userId),
    authorityList: parseAuthorities(groupUser.authorities)
  })).filter(userGroup => userGroup.authorityList.length)
}

const reloadGroupUsers = (groupCode, config) => loadGroupUsers(groupCode, config).then(data => {
  configModel.value.groupCode = groupCode
  configModel.value.userGroups = processGroupUsers(groupCode, data)
  return data
})

const toConfigGroupUsers = async (groupCode) => {
  if (!users.value?.length) {
    await loadSelectUsers()
  }
  return reloadGroupUsers(groupCode, { loading: true })
    .then(() => {
      showWindow.value = true
    })
}

const newUser = () => {
  currentUserGroup.value = {
    userIds: [],
    authorityList: [AUTHORITY_TYPE.READABLE],
    isNew: true
  }
  showEditWindow.value = true
}

const editUser = item => {
  currentUserGroup.value = { ...item, authorityList: [...item.authorityList] }
  showEditWindow.value = true
}

const removeUser = item => {
  return $coreConfirm($i18nBundle('common.msg.deleteConfirm')).then(() => {
    configModel.value.userGroups.splice(configModel.value.userGroups.indexOf(item), 1)
    return saveGroupUsersConfig()
  })
}

const columns = computed(() => defineTableColumns([{
  labelKey: 'common.label.username',
  minWidth: '180px',
  formatter: item => item.user?.userName || item.userId
}, {
  labelKey: 'api.label.authorities',
  minWidth: '260px',
  slot: 'authorities'
}]))

const buttons = computed(() => defineTableButtons([{
  tooltip: $i18nBundle('common.label.edit'),
  icon: 'Edit',
  round: true,
  type: 'primary',
  click: editUser
}, {
  tooltip: $i18nBundle('common.label.delete'),
  icon: 'DeleteFilled',
  round: true,
  type: 'danger',
  click: removeUser
}]))

const editFormOptions = computed(() => defineFormOptions([{
  labelKey: 'common.label.username',
  prop: currentUserGroup.value?.isNew ? 'userIds' : 'userId',
  type: 'select',
  required: true,
  disabled: !currentUserGroup.value?.isNew,
  children: selectableUserOptions(currentUserGroup.value),
  attrs: {
    filterable: true,
    multiple: !!currentUserGroup.value?.isNew,
    collapseTags: true,
    collapseTagsTooltip: true,
    maxCollapseTags: 5
  }
}, {
  labelKey: 'api.label.authorities',
  prop: 'authorityList',
  type: 'checkbox-group',
  required: true,
  children: CONFIG_AUTHORITIES.map(authority => ({
    value: authority.value,
    slots: {
      default () {
        return <ElTag type={AUTHORITY_TYPE_MAPPING[authority.value]}>{ $i18nBundle(authority.labelKey) }</ElTag>
      }
    }
  }))
}]))

const saveConfigModel = computed(() => {
  const model = { ...configModel.value }
  model.userGroups = model.userGroups.map(userGroup => {
    const newUserGroup = omit(userGroup, ['user', 'isNew', 'userIds', 'authorityList'])
    newUserGroup.authorities = parseAuthorities(userGroup.authorityList ?? userGroup.authorities).join(',')
    return newUserGroup
  }).filter(userGroup => userGroup.userId && userGroup.authorities)
  return model
})

const emit = defineEmits(['savedGroupUsers'])

const saveGroupUsersConfig = () => {
  const model = saveConfigModel.value
  return saveUserGroups(model, { loading: true })
    .then(data => {
      if (data.success) {
        return reloadGroupUsers(model.groupCode, { loading: true }).then(() => emit('savedGroupUsers', model))
      }
    })
}

const saveUserGroup = item => {
  const userGroups = (item.userIds || [item.userId]).filter(Boolean).map(userId => ({
    ...item,
    userId,
    groupCode: configModel.value.groupCode,
    user: findUser(userId),
    isNew: false,
    authorityList: parseAuthorities(item.authorityList)
  }))
  const index = configModel.value.userGroups.findIndex(groupUser => groupUser.id ? groupUser.id === item.id : groupUser.userId === item.userId)
  index > -1 ? configModel.value.userGroups.splice(index, 1, userGroups[0]) : configModel.value.userGroups.unshift(...userGroups)
  return saveGroupUsersConfig()
}

defineExpose({
  toConfigGroupUsers
})

</script>

<template>
  <common-window
    v-model="showWindow"
    :title="$t('api.label.projectGroupUsers') + ' - ' + groupItem?.groupName"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    width="700px"
    :show-cancel="false"
    :ok-label="$t('common.label.close')"
  >
    <el-container class="flex-column">
      <el-container class="margin-bottom2">
        <el-button
          type="primary"
          :disabled="!selectableUserOptions().length"
          @click="newUser"
        >
          <common-icon icon="Plus" />
          {{ $t('common.label.add') }}
        </el-button>
      </el-container>
      <common-table
        :data="configModel.userGroups"
        :columns="columns"
        :buttons="buttons"
        :buttons-column-attrs="{ width: '130px', align: 'center' }"
      >
        <template #authorities="{ row }">
          <el-tag
            v-for="authority in parseAuthorities(row.authorityList)"
            :key="authority"
            :type="AUTHORITY_TYPE_MAPPING[authority]"
            effect="dark"
            size="small"
            class="margin-right1 margin-bottom1"
          >
            {{ $t(`api.label.authority_${authority}`) }}
          </el-tag>
        </template>
      </common-table>
    </el-container>
    <simple-edit-window
      v-model="currentUserGroup"
      v-model:show-edit-window="showEditWindow"
      :form-options="editFormOptions"
      :name="$t('api.label.authorities')"
      :save-current-item="saveUserGroup"
      label-width="100px"
      width="600px"
      :show-fullscreen="false"
    />
  </common-window>
</template>
