<script setup lang="jsx">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { $coreConfirm, formatDate, useBackUrl } from '@/utils'
import { useApiProjectItem } from '@/api/ApiProjectApi'
import { useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useDefaultPage } from '@/config'
import ApiProjectTaskApi from '@/api/ApiProjectTaskApi'
import { $i18nBundle } from '@/messages'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import { defineFormOptions } from '@/components/utils'
import { useFormStatus } from '@/consts/GlobalConstants'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import { ElTag } from 'element-plus'
import ApiProjectImport from '@/views/components/api/project/ApiProjectImport.vue'

const route = useRoute()
const projectCode = route.params.projectCode

const { goBack } = useBackUrl(`/api/projects/${projectCode}`)
const { projectItem, loadProjectItem } = useApiProjectItem(projectCode, false)

const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { keyword: '', page: useDefaultPage() },
  searchMethod: ApiProjectTaskApi.search
})
const loadProjectTasks = (pageNumber) => searchMethod(pageNumber)

onMounted(async () => {
  await loadProjectItem(projectCode)
  searchParam.value.projectId = projectItem.value.id
  loadProjectTasks(1)
})

const columns = [{
  labelKey: 'api.label.importName',
  prop: 'taskName'
}, {
  labelKey: 'common.label.status',
  minWidth: '100px',
  formatter (data) {
    return <DelFlagTag v-model={data.status} clickToToggle={true}
                       onToggleValue={(status) => ApiProjectTaskApi.saveOrUpdate({ ...data, status })}/>
  },
  attrs: {
    align: 'center'
  }
}, {
  labelKey: 'api.label.hasPassword',
  minWidth: '100px',
  formatter (data) {
    const type = data.sharePassword ? 'success' : 'danger'
    const text = data.sharePassword ? $i18nBundle('common.label.yes') : $i18nBundle('common.label.no')
    return <ElTag type={type}>
      {text}
    </ElTag>
  },
  attrs: {
    align: 'center'
  }
}, {
  labelKey: 'common.label.createDate',
  property: 'createDate',
  dateFormat: 'YYYY-MM-DD HH:mm:ss'
}, {
  labelKey: 'api.label.expireDate',
  formatter: (row) => {
    if (row.expireDate) {
      return formatDate(row.expireDate)
    }
    return $i18nBundle('api.label.noExpires')
  }
}]

const buttons = computed(() => {
  return [{
    labelKey: 'api.label.copyLinkAndPassword',
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
const currentShare = ref()
const newOrEdit = async (id) => {
  if (id) {
    await ApiProjectTaskApi.getById(id).then(data => {
      data.resultData && (currentShare.value = data.resultData)
    })
  } else {
    currentShare.value = {
      projectId: projectItem.value.id,
      status: 1,
      exportEnabled: true,
      debugEnabled: true
    }
  }
  showEditWindow.value = true
}
const editFormOptions = computed(() => defineFormOptions([{
  labelKey: 'api.label.shareName',
  prop: 'shareName',
  required: true
}, useFormStatus(), {
  labelKey: 'api.label.exportEnabled',
  prop: 'exportEnabled',
  type: 'switch'
}, {
  labelKey: 'api.label.debugEnabled',
  prop: 'debugEnabled',
  type: 'switch'
}, {
  labelKey: 'api.label.accessPassword',
  prop: 'sharePassword',
  attrs: {
    showPassword: true
  }
}, {
  labelKey: 'api.label.expireDate',
  prop: 'expireDate',
  type: 'date-picker'
}]))

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
    <el-tabs
      type="border-card"
      lazy
    >
      <el-tab-pane>
        <template #label>
          {{ $t('api.label.manualImportData') }}
        </template>
        <api-project-import
          ref="importRef"
          :project="projectItem"
        />
      </el-tab-pane>
      <el-tab-pane>
        <template #label>
          {{ $t('api.label.autoImportData') }}
        </template>
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
          buttons-slot="buttons"
          :loading="loading"
          @page-size-change="loadProjectTasks()"
          @current-page-change="loadProjectTasks()"
        />
      </el-tab-pane>
    </el-tabs>
    <simple-edit-window
      v-model="currentShare"
      v-model:show-edit-window="showEditWindow"
      :form-options="editFormOptions"
      :name="$t('api.label.shareDocs')"
      :save-current-item="saveProjectTask"
      label-width="130px"
    />
  </el-container>
</template>

<style scoped>

</style>
