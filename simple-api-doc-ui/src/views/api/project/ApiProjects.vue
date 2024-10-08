<script setup lang="jsx">
import { computed, onMounted, onActivated, ref } from 'vue'
import { useDefaultPage } from '@/config'
import { useTableAndSearchForm } from '@/hooks/CommonHooks'
import { defineFormOptions } from '@/components/utils'
import { useAllUsers } from '@/api/ApiUserApi'
import ApiProjectApi, { copyProject } from '@/api/ApiProjectApi'
import { $coreConfirm, $goto, formatDate, isAdminUser, useCurrentUserName } from '@/utils'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import { $i18nBundle } from '@/messages'
import { useFormStatus } from '@/consts/GlobalConstants'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import { chunk } from 'lodash-es'
import CommonIcon from '@/components/common-icon/index.vue'
import { useRoute } from 'vue-router'
import { useWindowSize } from '@vueuse/core'
import ApiProjectImportWindow from '@/views/components/api/project/ApiProjectImportWindow.vue'
import { ElMessage } from 'element-plus'

const { width } = useWindowSize()

const route = useRoute()

const { search, getById, deleteById, saveOrUpdate } = ApiProjectApi

const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { page: useDefaultPage(50) },
  searchMethod: search
})
const loadApiProjects = (pageNumber) => {
  searchMethod(pageNumber)
}
const { userOptions, loadUsersAndRefreshOptions } = useAllUsers(searchParam)

onMounted(() => {
  loadApiProjects()
})

onActivated(async () => {
  await loadUsersAndRefreshOptions()
  loadApiProjects()
})
const toEditProject = (project) => {
  $goto(`/api/projects/${project.projectCode}?backUrl=${route.fullPath}`)
}

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
      loadApiProjects(1)
    }
  },
  {
    labelKey: 'common.label.keywords',
    prop: 'keyword'
  }]
})

const deleteProject = (project, $event) => {
  $event?.stopPropagation()
  $coreConfirm($i18nBundle('common.msg.commonDeleteConfirm', [project.projectName]))
    .then(() => deleteById(project.id))
    .then(() => loadApiProjects())
}

const deleteProjects = () => {
  $coreConfirm($i18nBundle('common.msg.deleteConfirm'))
    .then(() => ApiProjectApi.removeByIds(selectedRows.value.map(item => item.id)), { loading: true })
    .then(() => loadApiProjects())
}

const showEditWindow = ref(false)
const currentProject = ref()
const newOrEdit = async (id, $event) => {
  $event?.stopPropagation()
  if (id) {
    await getById(id).then(data => {
      data.resultData && (currentProject.value = data.resultData)
    })
  } else {
    currentProject.value = {
      status: 1,
      privateFlag: true,
      userName: searchParam.value?.userName || useCurrentUserName()
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
  labelKey: 'api.label.projectName',
  prop: 'projectName',
  required: true
}, useFormStatus(), {
  labelKey: 'common.label.description',
  prop: 'description',
  attrs: {
    type: 'textarea'
  }
}]))
const saveProjectItem = (item) => {
  return saveOrUpdate(item).then(() => loadApiProjects())
}

const colSize = computed(() => {
  console.log('====================================', width.value, Math.floor(width.value / 400) || 1)
  return Math.floor(width.value / 420) || 1
})
const minWidth = '100px'

const tableProjectItems = computed(() => {
  return tableData.value.map(project => {
    return {
      project,
      projectItems: [{
        labelKey: 'common.label.status',
        formatter () {
          return <DelFlagTag v-model={project.status} clickToToggle={true}
                        onToggleValue={(status) => saveProjectItem({ ...project, status })} />
        }
      }, {
        labelKey: 'common.label.modifyDate',
        value: formatDate(project.modifyDate) || formatDate(project.createDate),
        enabled: !!project.modifyDate || !!project.createDate
      }, {
        labelKey: 'common.label.description',
        value: project.description,
        enabled: !!project.description
      }]
    }
  })
})

const dataRows = computed(() => {
  return chunk(tableProjectItems.value, colSize.value)
})

const selectedRows = computed(() => tableProjectItems.value.map(item => item.project).filter(project => project?.selected))

const showImportWindow = ref(false)

const importSuccessCallback = (apiProject) => {
  loadApiProjects()
  showImportWindow.value = false
  const message = $i18nBundle('api.msg.importFileSuccess', [apiProject?.projectName]) +
      ', ' + $i18nBundle('api.msg.gotoProjectDetails')
  $coreConfirm(message).then(() => {
    toEditProject(apiProject)
  })
}

const toCopyProject = (project, $event) => {
  $event?.stopPropagation()
  $coreConfirm($i18nBundle('common.msg.confirmCopy'))
    .then(() => copyProject(project.id))
    .then(() => {
      ElMessage.success($i18nBundle('common.msg.operationSuccess'))
      loadApiProjects()
    })
}

</script>

<template>
  <el-container
    class="flex-column"
  >
    <common-form
      inline
      :model="searchParam"
      :options="searchFormOptions"
      :submit-label="$t('common.label.search')"
      @submit-form="loadApiProjects"
    >
      <template #buttons>
        <el-button
          type="info"
          @click="newOrEdit()"
        >
          {{ $t('common.label.new') }}
        </el-button>
        <el-button
          v-if="selectedRows?.length"
          type="danger"
          @click="deleteProjects()"
        >
          {{ $t('common.label.delete') }}
        </el-button>
        <el-button
          type="success"
          @click="showImportWindow = true"
        >
          {{ $t('api.label.importData') }}
        </el-button>
      </template>
    </common-form>
    <el-empty
      v-if="!dataRows.length"
      v-loading="loading"
      :description="$t('common.msg.noData')"
    />
    <el-container
      v-else
      class="flex-column"
    >
      <el-row
        v-for="(dataRow, index) in dataRows"
        :key="index"
        :gutter="20"
        :class="{'margin-top2': index>0}"
      >
        <el-col
          v-for="{project, projectItems} in dataRow"
          :key="project.id"
          :span="Math.floor(24/colSize)"
          @mouseenter="project.showOperations=true"
          @mouseleave="project.showOperations=false"
        >
          <el-card
            shadow="hover"
            class="small-card operation-card pointer"
            style="border-radius: 15px;"
            :class="{'project-selected': project.selected}"
            @click="toEditProject(project)"
          >
            <div
              class="card-header"
              style="padding-bottom: 12px"
            >
              <el-checkbox
                v-model="project.selected"
                style="margin-right: auto;"
                @click="$event.stopPropagation()"
              >
                <el-text
                  tag="b"
                  :type="project.status===1?'':'danger'"
                >
                  {{ project.projectName }}
                </el-text>
              </el-checkbox>
              <el-button
                v-if="project.showOperations"
                v-common-tooltip="$t('common.label.edit')"
                type="primary"
                size="small"
                round
                @click="newOrEdit(project.id, $event)"
              >
                <common-icon icon="Edit" />
              </el-button>
              <el-button
                v-if="project.showOperations"
                v-common-tooltip="$t('common.label.copy')"
                type="warning"
                size="small"
                round
                @click="toCopyProject(project, $event)"
              >
                <common-icon icon="FileCopyFilled" />
              </el-button>
              <el-button
                v-if="project.showOperations"
                v-common-tooltip="$t('common.label.delete')"
                type="danger"
                size="small"
                round
                @click="deleteProject(project, $event)"
              >
                <common-icon icon="DeleteFilled" />
              </el-button>
            </div>
            <common-descriptions
              :column="1"
              :min-width="minWidth"
              :items="projectItems"
            />
          </el-card>
        </el-col>
      </el-row>
    </el-container>
    <simple-edit-window
      v-model="currentProject"
      v-model:show-edit-window="showEditWindow"
      :form-options="editFormOptions"
      :name="$t('api.label.apiProjects')"
      :save-current-item="saveProjectItem"
      label-width="130px"
    />
    <api-project-import-window
      v-model="showImportWindow"
      :auto-alert="false"
      @import-success="importSuccessCallback"
    />
  </el-container>
</template>

<style scoped>
.project-selected {
  border-color: var(--el-color-primary);
}
</style>
