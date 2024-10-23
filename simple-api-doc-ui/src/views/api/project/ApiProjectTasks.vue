<script setup lang="jsx">
import { computed, onActivated, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { $coreAlert, $coreConfirm, $goto, isAdminUser, useBackUrl } from '@/utils'
import { useApiProjectItem, useSelectProjects } from '@/api/ApiProjectApi'
import { useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useDefaultPage } from '@/config'
import ApiProjectTaskApi, { triggerTask } from '@/api/ApiProjectTaskApi'
import { $i18nBundle } from '@/messages'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import { defineFormOptions } from '@/components/utils'
import { useFormStatus } from '@/consts/GlobalConstants'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import ApiProjectImport from '@/views/components/api/project/ApiProjectImport.vue'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import {
  AUTH_TYPE,
  IMPORT_AUTH_TYPES,
  IMPORT_SOURCE_TYPES,
  IMPORT_TASK_TYPES, TASK_STATUS_MAPPING,
  TASK_TRIGGER_RATES
} from '@/consts/ApiConstants'
import { AUTH_OPTION_CONFIG } from '@/services/api/ApiAuthorizationService'
import { isFunction } from 'lodash-es'
import { useFolderTreeNodes } from '@/services/api/ApiFolderService'
import dayjs from 'dayjs'
import { ElTag } from 'element-plus'
import { useAllUsers } from '@/api/ApiUserApi'

const route = useRoute()
const projectCode = route.params.projectCode
const inProject = !!projectCode
const { goBack } = useBackUrl(`/api/projects/${projectCode}`)
const { projectItem, loadProjectItem } = useApiProjectItem(projectCode, { autoLoad: false, detail: false })
const { folderTreeNodes, loadValidFolders } = useFolderTreeNodes()

const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { keyword: '', page: useDefaultPage() },
  searchMethod: ApiProjectTaskApi.search
})
const loadProjectTasks = (pageNumber) => searchMethod(pageNumber)
const { userOptions, loadUsersAndRefreshOptions } = useAllUsers(searchParam)
const { projectOptions, loadProjectsAndRefreshOptions } = useSelectProjects(searchParam)

onMounted(async () => {
  if (inProject) {
    await loadProjectItem(projectCode)
    const projectId = projectItem.value?.id
    searchParam.value.projectId = projectId
    searchParam.value.userName = projectItem.value?.userName
    loadValidFolders(searchParam.value.projectId)
  } else {
    loadUsersAndRefreshOptions()
    loadProjectsAndRefreshOptions()
  }
  loadProjectTasks(1)
})

onActivated(async () => {
  if (!inProject) {
    await Promise.allSettled([loadUsersAndRefreshOptions(), loadProjectsAndRefreshOptions()])
  }
  loadProjectTasks()
})

const columns = [{
  labelKey: 'api.label.taskName',
  prop: 'taskName',
  minWidth: '120px'
}, {
  labelKey: 'api.label.project',
  prop: 'project.projectName',
  minWidth: '120px',
  click (item) {
    if (!inProject && item.project?.projectCode) {
      $goto(`/api/projects/${item.project?.projectCode}?backUrl=${route.fullPath}`)
    } else {
      goBack()
    }
  }
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
    click: item => {
      triggerTask(item.id, { loading: true, timeout: 60000 })
        .then((data) => {
          if (data.success) {
            $coreAlert($i18nBundle('api.msg.importFileSuccess', [data.resultData?.projectName]))
            loadProjectTasks()
          }
        })
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
        .then(() => ApiProjectTaskApi.deleteById(item.id))
        .then(() => loadProjectTasks())
    }
  }]
})
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
        await loadProjectsAndRefreshOptions()
        loadProjectTasks(1)
      }
    }, {
      labelKey: 'api.label.project',
      prop: 'projectId',
      type: 'select',
      enabled: !inProject && projectOptions.value.length > 1,
      children: projectOptions.value,
      change () {
        loadProjectTasks(1)
      }
    },
    {
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
      taskType: IMPORT_TASK_TYPES[1].value,
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
  return defineFormOptions([{
    labelKey: 'api.label.taskName',
    prop: 'taskName',
    required: true
  }, {
    labelKey: 'api.label.project',
    prop: 'projectId',
    required: true,
    type: 'select',
    enabled: !inProject,
    disabled: !!currentModel.value?.id,
    children: projectOptions.value,
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
        return !currentModel.value.sourceUrl || /^https?:\/\//.test(currentModel.value.sourceUrl.trim())
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

const importRef = ref()

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
    <div class="admin-project-tasks" />
    <el-tabs
      v-show="inProject"
      lazy
    >
      <el-tab-pane>
        <template #label>
          {{ $t('api.label.autoImportData') }}
        </template>
        <Teleport
          defer
          to=".admin-project-tasks"
          :disabled="inProject"
        >
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
        </Teleport>
      </el-tab-pane>
      <el-tab-pane>
        <template #label>
          {{ $t('api.label.manualImportData') }}
        </template>
        <el-container class="form-edit-width-70 padding-top2">
          <api-project-import
            v-if="projectItem"
            ref="importRef"
            show-buttons
            :project="projectItem"
            @import-success="goBack"
          />
        </el-container>
      </el-tab-pane>
    </el-tabs>
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
