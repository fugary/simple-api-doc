<script setup lang="jsx">
import { computed, onActivated, onMounted, ref } from 'vue'
import { $coreConfirm, isAdminUser, useCurrentUserName } from '@/utils'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useDefaultPage } from '@/config'
import ApiProjectGroupApi from '@/api/ApiProjectGroupApi'
import { $i18nBundle } from '@/messages'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import { defineFormOptions } from '@/components/utils'
import { useFormStatus, useSearchStatus } from '@/consts/GlobalConstants'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import ApiGroupUsersConfigWindow from '@/views/components/api/project/ApiGroupUsersConfigWindow.vue'
import { AUTHORITY_TYPE } from '@/consts/ApiConstants'
import { ElTag } from 'element-plus'
import { useAllUsers } from '@/api/ApiUserApi'

const apiUsers = ref([])
const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { keyword: '', page: useDefaultPage() },
  searchMethod: ApiProjectGroupApi.search,
  dataProcessor: (data) => {
    apiUsers.value = []
    if (data.addons.users?.length) {
      apiUsers.value = data.addons.users
    }
    return data.resultData
  }
})
const loadProjectGroups = (pageNumber) => searchMethod(pageNumber)
const { userOptions, loadUsersAndRefreshOptions } = useAllUsers(searchParam)
const { initLoadOnce } = useInitLoadOnce(async () => {
  loadUsersAndRefreshOptions()
  loadProjectGroups()
})

onMounted(initLoadOnce)

onActivated(initLoadOnce)

const AUTHORITY_ITEMS = {
  [AUTHORITY_TYPE.READABLE]: ['R', 'api.label.authority_readable'],
  [AUTHORITY_TYPE.WRITABLE]: ['W', 'api.label.authority_writable'],
  [AUTHORITY_TYPE.DELETABLE]: ['D', 'api.label.authority_deletable']
}
const authValues = Object.keys(AUTHORITY_ITEMS)

const parseAuthorities = authorities => (authorities || '').split(/\s*,\s*/)
  .filter(authority => authValues.includes(authority))
  .sort((key1, key2) => authValues.indexOf(key1) - authValues.indexOf(key2))

const getAuthorityCode = authorities => authorities.map(authority => AUTHORITY_ITEMS[authority][0]).join('')

const getAuthorityTooltip = authorities => authorities.map(authority => {
  const item = AUTHORITY_ITEMS[authority]
  return `${item[0]}: ${$i18nBundle(item[1])}`
}).join('<br/>')

const columns = computed(() => {
  const apiUserMap = Object.fromEntries(apiUsers.value.map(apiUser => [apiUser.id, apiUser]))
  return [{
    labelKey: 'api.label.projectGroupName',
    prop: 'groupName',
    minWidth: '120px'
  }, {
    labelKey: 'common.label.status',
    formatter (data) {
      return <DelFlagTag v-model={data.status} clickToToggle={true}
                         onToggleValue={(status) => saveProjectGroup({ ...data, status })}/>
    },
    attrs: {
      align: 'center'
    }
  }, {
    labelKey: 'api.label.projectGroupUsers1',
    formatter (data) {
      const groupUsers = (data.userGroups || []).map(userGroup => ({
        apiUser: apiUserMap[userGroup.userId],
        authorities: parseAuthorities(userGroup.authorities)
      })).filter(({ apiUser, authorities }) => apiUser && authorities.length)
      const groupComps = groupUsers.map(({ apiUser, authorities }, index) => {
        return <>
          <span class="group-authority-inline">
            <span class="group-authority-inline__name">{apiUser.userName}</span>
            <ElTag v-common-tooltip={getAuthorityTooltip(authorities)}
                   size="small" type="primary" effect="plain" class="group-authority-inline__tag">
              {getAuthorityCode(authorities)}
            </ElTag>
          </span>
          {index < groupUsers.length - 1 ? <span class="group-authority-inline__separator">, </span> : ''}
        </>
      })
      return groupComps.length ? <div class="group-authority-list">{groupComps}</div> : ''
    },
    minWidth: '180px'
  }, {
    labelKey: 'common.label.description',
    property: 'description',
    minWidth: '150px'
  }]
})

const groupUsersConfigRef = ref()

const buttons = computed(() => {
  return [{
    labelKey: 'api.label.projectGroupUsers',
    type: 'success',
    click: item => {
      groupUsersConfigRef.value?.toConfigGroupUsers(item.groupCode)
    }
  }, {
    labelKey: 'common.label.edit',
    type: 'primary',
    click: item => {
      newOrEdit(item.id)
    }
  }, {
    labelKey: 'common.label.delete',
    type: 'danger',
    click: item => {
      $coreConfirm($i18nBundle('common.msg.deleteConfirm'))
        .then(() => ApiProjectGroupApi.deleteById(item.id))
        .then(() => loadProjectGroups())
    }
  }]
})
//* ************搜索框**************//
const searchFormOptions = computed(() => {
  return [{
    labelKey: 'common.label.user',
    prop: 'userName',
    type: 'select',
    enabled: isAdminUser(),
    children: userOptions.value,
    attrs: {
      clearable: false
    },
    change () {
      loadProjectGroups(1)
    }
  }, useSearchStatus({
    change: () => loadProjectGroups(1)
  }), {
    labelKey: 'common.label.keywords',
    prop: 'keyword'
  }]
})

const showEditWindow = ref(false)
const currentModel = ref()
const newOrEdit = async (id) => {
  if (id) {
    await ApiProjectGroupApi.getById(id).then(data => {
      if (data.resultData) {
        currentModel.value = data.resultData
      }
    })
  } else {
    currentModel.value = {
      status: 1,
      userName: searchParam.value?.userName || useCurrentUserName()
    }
  }
  showEditWindow.value = true
}

const editFormOptions = computed(() => {
  return defineFormOptions([{
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
  }])
})

const saveProjectGroup = (data) => {
  return ApiProjectGroupApi.saveOrUpdate(data)
    .then(() => loadProjectGroups())
}

</script>

<template>
  <el-container class="flex-column">
    <common-form
      inline
      :model="searchParam"
      :options="searchFormOptions"
      :submit-label="$t('common.label.search')"
      @submit-form="loadProjectGroups(1)"
    >
      <template #buttons>
        <el-button
          type="info"
          @click="newOrEdit()"
        >
          {{ $t('common.label.new') }}
        </el-button>
      </template>
    </common-form>
    <common-table
      v-model:page="searchParam.page"
      :data="tableData"
      :columns="columns"
      :buttons="buttons"
      :buttons-column-attrs="{minWidth:'200px'}"
      buttons-slot="buttons"
      :loading="loading"
      @page-size-change="loadProjectGroups()"
      @current-page-change="loadProjectGroups()"
      @row-dblclick="newOrEdit($event.id)"
    />
    <simple-edit-window
      v-model="currentModel"
      v-model:show-edit-window="showEditWindow"
      :form-options="editFormOptions"
      :name="$t('api.label.projectGroups1')"
      :save-current-item="saveProjectGroup"
      label-width="130px"
    />
    <api-group-users-config-window
      ref="groupUsersConfigRef"
      @saved-group-users="loadProjectGroups()"
    />
  </el-container>
</template>

<style scoped>
.group-authority-list {
  display: -webkit-box;
  overflow: hidden;
  line-height: 24px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.group-authority-inline {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  vertical-align: middle;
}

.group-authority-inline__name {
  display: inline-block;
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.group-authority-inline__tag {
  margin-left: 4px;
  cursor: pointer;
  vertical-align: middle;
}

.group-authority-inline__separator {
  margin-right: 4px;
  color: var(--el-text-color-secondary);
}
</style>
