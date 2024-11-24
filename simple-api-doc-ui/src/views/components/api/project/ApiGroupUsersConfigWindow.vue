<script setup lang="jsx">
import { computed, ref } from 'vue'
import { useAllUsers } from '@/api/ApiUserApi'
import { loadGroupUsers, saveUserGroups } from '@/api/ApiProjectGroupApi'
import CommonFormControl from '@/components/common-form-control/index.vue'
import { ALL_AUTHORITIES, AUTHORITY_TYPE, AUTHORITY_TYPE_MAPPING } from '@/consts/ApiConstants'
import { omit } from 'lodash-es'
import { isUserAdmin } from '@/utils'
import { $i18nBundle } from '@/messages'
import { ElTag } from 'element-plus'

const { users, loadSelectUsers } = useAllUsers()

const showWindow = defineModel({
  type: Boolean,
  default: false
})

const configModel = ref({
  groupCode: '',
  userGroups: []
})

const groupItem = ref()

const processGroupUsers = (groupCode, data) => {
  const groupUsers = data.resultData || []
  groupItem.value = data.addons?.group
  const groupUsersMap = Object.fromEntries(groupUsers.map(groupUser => [groupUser.userId, groupUser]))
  return users.value.map(user => {
    const groupUser = groupUsersMap[user.id]
    return {
      userId: user.id,
      groupCode,
      user,
      group: groupItem.value,
      authorityList: groupUser?.authorities ? groupUser.authorities.split(',') : [],
      ...groupUser,
      formOption: {
        showLabel: false,
        prop: 'authorityList',
        type: 'checkbox-group',
        children: ALL_AUTHORITIES.map(allAuthority => {
          return {
            value: allAuthority.value,
            slots: {
              default () {
                return <ElTag type={AUTHORITY_TYPE_MAPPING[allAuthority.value]}>{ $i18nBundle(allAuthority.labelKey) }</ElTag>
              }
            }
          }
        }),
        change (value) {
          if (value?.length >= 2 && value.includes(AUTHORITY_TYPE.FORBIDDEN)) {
            const item = configModel.value.userGroups.find(userGroup => userGroup.userId === user.id)
            if (item) {
              if (value[0] === AUTHORITY_TYPE.FORBIDDEN) {
                item.authorityList.shift() // 清除FORBIDDEN
              } else if (value[value.length - 1] === AUTHORITY_TYPE.FORBIDDEN) {
                item.authorityList = [AUTHORITY_TYPE.FORBIDDEN] // 清空现有权限并设置为 FORBIDDEN
              }
            }
          }
        }
      }
    }
  })
}

const toConfigGroupUsers = async (groupCode) => {
  if (!users.value?.length) {
    await loadSelectUsers()
  }
  loadGroupUsers(groupCode, { loading: true })
    .then(data => {
      configModel.value.groupCode = groupCode
      configModel.value.userGroups = processGroupUsers(groupCode, data)
      showWindow.value = true
    })
}

const columns = computed(() => {
  return [{
    labelKey: 'common.label.username',
    property: 'user.userName'
  }, {
    labelKey: 'api.label.authorities',
    formatter: (item, column, cellValue, index) => {
      if (isUserAdmin(item.user?.userName) || item.user?.userName === item.group?.userName) {
        return <ElTag type="success">{$i18nBundle('api.label.authorityFullAccess')}</ElTag>
      }
      return <CommonFormControl option={item.formOption} model={item} prop={`userGroups[${index}].${item.formOption.prop}`}/>
    },
    minWidth: '200px'
  }]
})

const saveConfigModel = computed(() => {
  const model = { ...configModel.value }
  model.userGroups = model.userGroups.map(userGroup => {
    const authorities = userGroup.authorityList?.length ? userGroup.authorityList.join(',') : ''
    const newUserGroup = omit(userGroup, ['user', 'group', 'formOption', 'authorityList'])
    newUserGroup.authorities = authorities
    return newUserGroup
  }).filter(userGroup => !!userGroup.authorities)
  return model
})

const emit = defineEmits(['savedGroupUsers'])

const saveGroupUsersConfig = () => {
  console.log('=============================', saveConfigModel.value)
  saveUserGroups(saveConfigModel.value)
    .then(data => {
      console.log('=============================data', data)
      if (data.success) {
        emit('savedGroupUsers', saveConfigModel.value)
        showWindow.value = false
      }
    })
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
    width="800px"
    :ok-click="saveGroupUsersConfig"
    :ok-label="$t('common.label.save')"
  >
    <el-container class="flex-column">
      <common-table
        :data="configModel.userGroups"
        :columns="columns"
      />
    </el-container>
  </common-window>
</template>

<style scoped>

</style>
