<script setup lang="jsx">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { $coreConfirm, useBackUrl } from '@/utils'
import { useApiProjectItem } from '@/api/ApiProjectApi'
import { useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useDefaultPage } from '@/config'
import ApiProjectTaskApi from '@/api/ApiProjectTaskApi'
import { $i18nBundle } from '@/messages'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import { defineFormOptions } from '@/components/utils'
import { useFormStatus } from '@/consts/GlobalConstants'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import ApiProjectImport from '@/views/components/api/project/ApiProjectImport.vue'
import {
  AUTH_TYPE,
  IMPORT_AUTH_TYPES,
  IMPORT_SOURCE_TYPES,
  IMPORT_TASK_TYPES,
  TASK_TRIGGER_RATES
} from '@/consts/ApiConstants'
import { AUTH_OPTION_CONFIG } from '@/services/api/ApiAuthorizationService'
import { isFunction } from 'lodash-es'
import { useFolderTreeNodes } from '@/services/api/ApiFolderService'
import dayjs from 'dayjs'

const route = useRoute()
const projectCode = route.params.projectCode
const { goBack } = useBackUrl(`/api/projects/${projectCode}`)
const { projectItem, loadProjectItem } = useApiProjectItem(projectCode, false)
const { folderTreeNodes, folders, loadValidFolders } = useFolderTreeNodes()

const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { keyword: '', page: useDefaultPage() },
  searchMethod: ApiProjectTaskApi.search
})
const loadProjectTasks = (pageNumber) => searchMethod(pageNumber)

onMounted(async () => {
  await loadProjectItem(projectCode)
  searchParam.value.projectId = projectItem.value.id
  loadProjectTasks(1)
  loadValidFolders(searchParam.value.projectId)
})

const columns = [{
  labelKey: 'api.label.taskName',
  prop: 'taskName'
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
  labelKey: 'api.label.targetFolder',
  formatter (data) {
    return folders.value.find(folder => folder.id === data.toFolder)?.folderName
  }
}, {
  labelKey: 'api.label.source',
  formatter (data) {
    const labelKey = IMPORT_SOURCE_TYPES.find(type => type.value === data.sourceType)?.labelKey
    return labelKey ? $i18nBundle(labelKey) : ''
  }
}, {
  labelKey: 'api.label.importData',
  formatter (data) {
    const labelKey = IMPORT_TASK_TYPES.find(type => type.value === data.taskType)?.labelKey
    return labelKey ? $i18nBundle(labelKey) : ''
  }
}, {
  labelKey: 'api.label.triggerRate',
  formatter (data) {
    return data.taskType === 'auto' ? dayjs.duration(data.scheduleRate, 'minutes') : $i18nBundle('api.label.manualImportData1')
  }
}, {
  labelKey: 'api.label.execDate',
  property: 'execDate',
  dateFormat: 'YYYY-MM-DD HH:mm:ss'
}]

const buttons = computed(() => {
  return [{
    labelKey: 'api.label.importNow',
    type: 'success'
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
      data.resultData && (currentModel.value = data.resultData)
    })
  } else {
    currentModel.value = {
      projectId: projectItem.value.id,
      status: 1,
      taskType: IMPORT_TASK_TYPES[0].value,
      sourceType: IMPORT_SOURCE_TYPES[0].value,
      authType: AUTH_TYPE.NONE,
      toFolder: folderTreeNodes.value[0]?.id
    }
  }
  showEditWindow.value = true
}

const editFormOptions = computed(() => {
  let authOptions = AUTH_OPTION_CONFIG[currentModel.value?.authType]?.options || []
  if (isFunction(authOptions)) {
    authOptions = authOptions()
  }
  return defineFormOptions([{
    labelKey: 'api.label.taskName',
    prop: 'taskName',
    required: true
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
        return !currentModel.value.sourceUrl || /^https?:\/\//.test(currentModel.value.sourceUrl)
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
    }
  }])
})

const saveProjectTask = (item) => {
  return ApiProjectTaskApi.saveOrUpdate(item).then(() => loadProjectTasks())
}

const importRef = ref()

</script>

<template>
  <el-container class="flex-column">
    <el-page-header
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
    <el-tabs lazy>
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
            :back-url="goBack"
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
            :buttons-column-attrs="{width:'250px'}"
            buttons-slot="buttons"
            :loading="loading"
            @page-size-change="loadProjectTasks()"
            @current-page-change="loadProjectTasks()"
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
