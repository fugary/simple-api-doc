<script setup lang="jsx">
import { computed, onActivated, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { $coreAlert, $coreConfirm, $goto, checkShowColumn, isAdminUser, useBackUrl } from '@/utils'
import { useApiProjectItem, useSelectProjects } from '@/api/ApiProjectApi'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useDefaultPage } from '@/config'
import ApiProjectTaskApi, { copyProjectTask, triggerTask } from '@/api/ApiProjectTaskApi'
import { $i18nBundle, $i18nKey } from '@/messages'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import { defineFormOptions } from '@/components/utils'
import { useFormStatus, useSearchStatus } from '@/consts/GlobalConstants'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import ApiProjectImport from '@/views/components/api/project/ApiProjectImport.vue'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import {
  AUTH_TYPE, AUTHORITY_TYPE,
  IMPORT_AUTH_TYPES,
  IMPORT_SOURCE_TYPES,
  IMPORT_TASK_TYPES, TASK_STATUS_MAPPING,
  TASK_TRIGGER_RATES
} from '@/consts/ApiConstants'
import { AUTH_OPTION_CONFIG } from '@/services/api/ApiAuthorizationService'
import { isFunction } from 'lodash-es'
import { useFolderTreeNodes } from '@/services/api/ApiFolderService'
import dayjs from 'dayjs'
import { ElLink, ElTag, ElText } from 'element-plus'
import { useAllUsers } from '@/api/ApiUserApi'
import { inProjectCheckAccess, useSelectProjectGroups } from '@/api/ApiProjectGroupApi'

const route = useRoute()
const projectCode = route.params.projectCode
const inProject = !!projectCode
const { goBack } = useBackUrl(`/api/projects/${projectCode}`)
const { projectItem, loadProjectItem } = useApiProjectItem(projectCode, { autoLoad: false, detail: false })
const { folderTreeNodes, loadValidFolders } = useFolderTreeNodes()

const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { keyword: '', page: useDefaultPage() },
  dataProcessor: data => (data?.resultData || []).map(dataItem => {
    dataItem.isDeletable = projectCheckAccess(dataItem.project.groupCode, AUTHORITY_TYPE.DELETABLE)
    dataItem.isWritable = projectCheckAccess(dataItem.project.groupCode, AUTHORITY_TYPE.WRITABLE) || dataItem.isDeletable
    return dataItem
  }),
  searchMethod: ApiProjectTaskApi.search
})
const loadProjectTasks = (pageNumber) => searchMethod(pageNumber)
const { userOptions, loadUsersAndRefreshOptions } = useAllUsers(searchParam)
const { projectOptions, loadProjectsAndRefreshOptions } = useSelectProjects(searchParam)
const { projectCheckAccess, projectGroupOptions, loadGroupsAndRefreshOptions } = useSelectProjectGroups(searchParam)
const { initLoadOnce } = useInitLoadOnce(async () => {
  if (inProject) {
    await loadProjectItem(projectCode)
    const projectId = projectItem.value?.id
    searchParam.value.projectId = projectId
    searchParam.value.userName = projectItem.value?.userName
    searchParam.value.groupCode = projectItem.value?.groupCode
    loadValidFolders(searchParam.value.projectId)
  } else {
    await Promise.allSettled([loadUsersAndRefreshOptions(), loadGroupsAndRefreshOptions(), loadProjectsAndRefreshOptions()])
  }
  await loadProjectTasks()
})

onMounted(initLoadOnce)

onActivated(initLoadOnce)

const columns = [{
  labelKey: 'api.label.taskName',
  prop: 'taskName',
  minWidth: '120px'
}, {
  labelKey: 'api.label.project',
  formatter (data) {
    const url = `/api/projects/${data.project?.projectCode}?backUrl=${route.fullPath}`
    let groupInfo = ''
    if (data.project?.groupCode) {
      groupInfo = projectGroupOptions.value.find(group => group.value === data.project?.groupCode)?.label
    }
    return <>
      <ElLink type="primary" onClick={() => $goto(url)}>{data.project?.projectName}</ElLink>
      {groupInfo ? <><br/><ElText type="info">{`(${groupInfo})`}</ElText></> : ''}
    </>
  },
  minWidth: '120px'
}, {
  labelKey: 'common.label.status',
  formatter (data) {
    return <DelFlagTag v-model={data.status} clickToToggle={true}
                       onToggleValue={(status) => saveProjectTask({ ...data, status })}/>
  },
  attrs: {
    align: 'center'
  }
}, {
  labelKey: 'api.label.runningStatus',
  enabled: checkShowColumn(tableData.value, 'taskStatus'),
  formatter (data) {
    if (data.taskStatus) {
      const type = TASK_STATUS_MAPPING[data.taskStatus] || 'info'
      return <ElTag type={type}>{data.taskStatus}</ElTag>
    }
  },
  attrs: {
    align: 'center'
  }
}, {
  labelKey: 'api.label.scheduleStatus',
  enabled: checkShowColumn(tableData.value, 'scheduleStatus'),
  formatter (data) {
    if (data.scheduleStatus) {
      return <ElTag type={data.scheduleStatus === 'started' ? 'success' : 'danger'}>
        {data.scheduleStatus}
      </ElTag>
    }
  },
  attrs: {
    align: 'center'
  }
}, {
  labelKey: 'api.label.source',
  formatter (data) {
    const labelKey = IMPORT_SOURCE_TYPES.find(type => type.value === data.sourceType)?.labelKey
    return labelKey ? $i18nBundle(labelKey) : ''
  }
}, {
  labelKey: 'api.label.triggerRate',
  formatter (data) {
    if (data.taskType === 'auto') {
      return data.taskType === 'auto' ? dayjs.duration(data.scheduleRate, 'seconds').humanize() : $i18nBundle('api.label.manualImportData1')
    }
    const labelKey = IMPORT_TASK_TYPES.find(type => type.value === data.taskType)?.labelKey
    return labelKey ? $i18nBundle(labelKey) : ''
  }
}, {
  labelKey: 'api.label.execDate',
  property: 'execDate',
  dateFormat: 'YYYY-MM-DD HH:mm:ss',
  minWidth: '120px'
}]

const buttons = computed(() => {
  return [{
    labelKey: 'api.label.importNow',
    type: 'success',
    buttonIf: item => item.isWritable,
    click: item => {
      $coreConfirm($i18nKey('common.msg.commonConfirm', 'api.label.importNow'))
        .then(() => triggerTask(item.id, { loading: true, timeout: 60000 })
          .then((data) => {
            if (data.success) {
              $coreAlert($i18nBundle('api.msg.importFileSuccess', [data.resultData?.projectName]))
              loadProjectTasks()
            }
          }))
    }
  }, {
    labelKey: 'common.label.edit',
    type: 'primary',
    buttonIf: item => item.isWritable,
    click: item => {
      newOrEdit(item.id)
    }
  }, {
    type: 'warning',
    labelKey: 'common.label.copy',
    buttonIf: item => item.isWritable,
    click: item => {
      $coreConfirm($i18nBundle('common.msg.confirmCopy'))
        .then(() => copyProjectTask(item.id))
        .then(() => loadProjectTasks())
    }
  }, {
    labelKey: 'common.label.delete',
    type: 'danger',
    buttonIf: item => item.isDeletable,
    click: item => {
      $coreConfirm($i18nBundle('common.msg.deleteConfirm'))
        .then(() => ApiProjectTaskApi.deleteById(item.id))
        .then(() => loadProjectTasks())
    }
  }]
})
const changeGroup = async groupCode => {
  searchParam.value.groupCode = groupCode
  tableData.value = []
  await loadProjectsAndRefreshOptions()
  return loadProjectTasks(1)
}
//* ************搜索框**************//
const searchFormOptions = computed(() => {
  return [
    {
      labelKey: 'common.label.user',
      prop: 'userName',
      type: 'select',
      enabled: !inProject && isAdminUser(),
      children: userOptions.value,
      attrs: {
        clearable: false
      },
      change: async () => {
        await loadGroupsAndRefreshOptions()
        await loadProjectsAndRefreshOptions()
        loadProjectTasks(1)
      }
    }, {
      labelKey: 'api.label.projectGroups1',
      prop: 'groupCode',
      type: 'select',
      enabled: !inProject && !!projectGroupOptions.value?.length,
      children: projectGroupOptions.value,
      change: changeGroup
    }, {
      labelKey: 'api.label.project',
      prop: 'projectId',
      type: 'select',
      enabled: !inProject && !!projectOptions.value.length,
      children: projectOptions.value,
      change () {
        loadProjectTasks(1)
      }
    }, useSearchStatus({
      change: () => loadProjectTasks(1)
    }), {
      labelKey: 'common.label.keywords',
      prop: 'keyword'
    }
  ]
})

const showEditWindow = ref(false)
const currentModel = ref()
const newOrEdit = async (id) => {
  if (id) {
    await ApiProjectTaskApi.getById(id).then(data => {
      if (data.resultData) {
        currentModel.value = data.resultData
        if (currentModel.value.authContent) {
          currentModel.value.authContentModel = JSON.parse(currentModel.value.authContent)
        }
        if (!inProject) {
          loadValidFolders(currentModel.value.projectId)
        }
      }
    })
  } else {
    currentModel.value = {
      projectId: projectItem.value?.id,
      status: 1,
      taskType: IMPORT_TASK_TYPES[0].value,
      sourceType: IMPORT_SOURCE_TYPES[0].value,
      authType: AUTH_TYPE.NONE,
      toFolder: inProject ? folderTreeNodes.value[0]?.id : undefined,
      scheduleRate: TASK_TRIGGER_RATES[TASK_TRIGGER_RATES.length - 1].value,
      authContentModel: {}
    }
    if (!inProject) {
      folderTreeNodes.value = []
    }
  }
  showEditWindow.value = true
}

const editFormOptions = computed(() => {
  let authOptions = AUTH_OPTION_CONFIG[currentModel.value?.authType]?.options || []
  if (isFunction(authOptions)) {
    authOptions = authOptions()
  }
  authOptions = authOptions.map(option => {
    return {
      ...option,
      prop: `authContentModel.${option.prop}`
    }
  })
  const filteredProjectOptions = projectOptions.value.map((project) => {
    project.isDeletable = projectCheckAccess(project.groupCode, AUTHORITY_TYPE.DELETABLE)
    project.isWritable = projectCheckAccess(project.groupCode, AUTHORITY_TYPE.WRITABLE) || project.isDeletable
    return project
  }).filter(project => project.isWritable)
  return defineFormOptions([{
    labelKey: 'api.label.taskName',
    prop: 'taskName',
    required: true
  }, {
    labelKey: 'api.label.projectGroups1',
    prop: 'groupCode',
    type: 'select',
    enabled: !inProject && !!projectGroupOptions.value?.length,
    children: projectGroupOptions.value,
    change (groupCode) {
      currentModel.value.projectId = null
      changeGroup(groupCode)
    }
  }, {
    labelKey: 'api.label.project',
    prop: 'projectId',
    required: true,
    type: 'select',
    enabled: !inProject,
    disabled: !!currentModel.value?.id && !currentModel.value?.taskName?.endsWith('-copy'),
    children: filteredProjectOptions,
    change: async (projectId) => {
      await loadValidFolders(projectId)
      currentModel.value.toFolder = folderTreeNodes.value[0]?.id
    }
  }, {
    labelKey: 'api.label.importData',
    prop: 'taskType',
    type: 'segmented',
    attrs: {
      clearable: false,
      options: IMPORT_TASK_TYPES.map(item => ({
        value: item.value,
        label: $i18nBundle(item.labelKey)
      }))
    }
  }, {
    enabled: currentModel.value?.taskType === 'auto',
    required: true,
    labelKey: 'api.label.triggerRate',
    prop: 'scheduleRate',
    type: 'select',
    children: TASK_TRIGGER_RATES,
    attrs: {
      clearable: false
    }
  }, useFormStatus(), {
    labelKey: 'api.label.source',
    prop: 'sourceType',
    type: 'radio-group',
    children: IMPORT_SOURCE_TYPES
  }, {
    required: true,
    labelKey: 'api.label.importUrl',
    prop: 'sourceUrl',
    rules: [{
      message: $i18nBundle('api.msg.proxyUrlMsg'),
      validator: () => {
        return !currentModel.value.sourceUrl || /^https?:\/\/.+/.test(currentModel.value.sourceUrl.trim())
      }
    }]
  }, {
    labelKey: 'api.label.authType',
    prop: 'authType',
    type: 'radio-group',
    children: IMPORT_AUTH_TYPES
  }, ...authOptions, {
    labelKey: 'api.label.targetFolder',
    type: 'tree-select',
    prop: 'toFolder',
    attrs: {
      checkStrictly: true,
      filterable: true,
      nodeKey: 'id',
      data: folderTreeNodes.value,
      clearable: false,
      defaultExpandedKeys: folderTreeNodes.value[0]?.id ? [folderTreeNodes.value[0]?.id] : []
    },
    slots: {
      default: ({ node }) => <TreeIconLabel node={node} iconLeaf="Folder"/>
    }
  }])
})

const saveProjectTask = (item) => {
  const modelParam = { ...item }
  if (modelParam.authType !== AUTH_TYPE.NONE) {
    modelParam.authContent = JSON.stringify(modelParam.authContentModel)
    delete modelParam.authContentModel
  }
  return ApiProjectTaskApi.saveOrUpdate(modelParam).then(() => loadProjectTasks())
}

const isWritable = computed(() => {
  return !inProject || inProjectCheckAccess(projectItem.value, AUTHORITY_TYPE.WRITABLE) || inProjectCheckAccess(projectItem.value, AUTHORITY_TYPE.DELETABLE)
})
</script>

<template>
  <el-container class="flex-column">
    <el-page-header
      v-if="inProject"
      class="margin-bottom3"
      @back="goBack"
    >
      <template #content>
        <el-container>
          <span>
            {{ projectItem?.projectName }} - {{ $t('api.label.importData') }}
          </span>
        </el-container>
      </template>
    </el-page-header>
    <el-tabs
      v-if="inProject"
      lazy
    >
      <el-tab-pane>
        <template #label>
          {{ $t('api.label.autoImportData') }}
        </template>
        <el-container class="flex-column padding-top2">
          <common-form
            inline
            :model="searchParam"
            :options="searchFormOptions"
            :submit-label="$t('common.label.search')"
            :back-url="inProject?goBack:''"
            @submit-form="loadProjectTasks(1)"
          >
            <template #buttons>
              <el-button
                v-if="isWritable"
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
            :buttons-column-attrs="{minWidth:'250px'}"
            buttons-slot="buttons"
            :loading="loading"
            @page-size-change="loadProjectTasks()"
            @current-page-change="loadProjectTasks()"
          />
        </el-container>
      </el-tab-pane>
      <el-tab-pane :disabled="!isWritable">
        <template #label>
          {{ $t('api.label.manualImportData') }}
        </template>
        <el-container class="form-edit-width-70 padding-top2">
          <api-project-import
            v-if="projectItem"
            show-buttons
            :project="projectItem"
            @import-success="goBack"
          />
        </el-container>
      </el-tab-pane>
    </el-tabs>
    <el-container
      v-else
      class="flex-column padding-top2"
    >
      <common-form
        inline
        :model="searchParam"
        :options="searchFormOptions"
        :submit-label="$t('common.label.search')"
        :back-url="inProject?goBack:''"
        @submit-form="loadProjectTasks(1)"
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
        @page-size-change="loadProjectTasks()"
        @current-page-change="loadProjectTasks()"
      />
    </el-container>
    <simple-edit-window
      v-model="currentModel"
      v-model:show-edit-window="showEditWindow"
      :form-options="editFormOptions"
      :name="$t('api.label.importData')"
      :save-current-item="saveProjectTask"
      label-width="130px"
    />
  </el-container>
</template>

<style scoped>

</style>
