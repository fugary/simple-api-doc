<script setup lang="jsx">
import { computed, onActivated, onMounted, ref } from 'vue'
import { $coreConfirm, isAdminUser, useCurrentUserName } from '@/utils'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useDefaultPage } from '@/config'
import ApiProjectGroupApi, { renderProjectGroupLabel } from '@/api/ApiProjectGroupApi'
import { $i18nBundle } from '@/messages'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import { defineFormOptions } from '@/components/utils'
import { useFormStatus, useSearchStatus } from '@/consts/GlobalConstants'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import ApiGroupUsersConfigWindow from '@/views/components/api/project/ApiGroupUsersConfigWindow.vue'
import { useAllUsers } from '@/api/ApiUserApi'
import ApiGroupAuthorityList from '@/views/components/api/project/ApiGroupAuthorityList.vue'

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

const columns = computed(() => {
  return [{
    labelKey: 'api.label.projectGroupName',
    formatter (data) {
      return renderProjectGroupLabel(data)
    },
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
      return <ApiGroupAuthorityList userGroups={data.userGroups} users={apiUsers.value}/>
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
