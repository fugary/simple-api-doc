<script setup lang="jsx">
import { computed, onActivated, onMounted, ref } from 'vue'
import { useDefaultPage } from '@/config'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import { defineFormOptions } from '@/components/utils'
import { useAllUsers } from '@/api/ApiUserApi'
import ApiProjectApi, { calcProjectIconUrl, copyProject, uploadFiles } from '@/api/ApiProjectApi'
import { $coreConfirm, $goto, formatDate, isAdminUser, useCurrentUserName } from '@/utils'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import { $i18nBundle } from '@/messages'
import { useFormStatus, useSearchStatus } from '@/consts/GlobalConstants'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import { chunk } from 'lodash-es'
import CommonIcon from '@/components/common-icon/index.vue'
import { useRoute } from 'vue-router'
import { useWindowSize } from '@vueuse/core'
import ApiProjectImportWindow from '@/views/components/api/project/ApiProjectImportWindow.vue'
import { ElMessage, ElUpload, ElText } from 'element-plus'
import { useSelectProjectGroups } from '@/api/ApiProjectGroupApi'
import { AUTHORITY_TYPE } from '@/consts/ApiConstants'

const { width } = useWindowSize()

const route = useRoute()

const { search, getById, deleteById, saveOrUpdate } = ApiProjectApi

const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { page: useDefaultPage(50) },
  dataProcessor: data => (data?.resultData || []).map(project => {
    project.isDeletable = projectCheckAccess(project.groupCode, AUTHORITY_TYPE.DELETABLE)
    project.isWritable = projectCheckAccess(project.groupCode, AUTHORITY_TYPE.WRITABLE) || project.isDeletable
    return project
  }),
  searchMethod: search
})
const loadApiProjects = (pageNumber) => {
  tableData.value = []
  return searchMethod(pageNumber)
}
const { userOptions, loadUsersAndRefreshOptions } = useAllUsers(searchParam)
const { projectCheckAccess, projectGroupOptions, loadGroupsAndRefreshOptions } = useSelectProjectGroups(searchParam)

const { initLoadOnce } = useInitLoadOnce(async () => {
  await loadUsersAndRefreshOptions()
  await loadGroupsAndRefreshOptions()
  await loadApiProjects()
})

onMounted(initLoadOnce)

onActivated(initLoadOnce)
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
      loadGroupsAndRefreshOptions()
      loadApiProjects(1)
    }
  }, {
    labelKey: 'api.label.projectGroups',
    prop: 'groupCode',
    type: 'select',
    enabled: !!projectGroupOptions.value?.length,
    children: projectGroupOptions.value,
    change () {
      loadApiProjects(1)
    }
  },
  useSearchStatus({ change: () => loadApiProjects(1) }),
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
const editProjectGroupOptions = computed(() => projectGroupOptions.value
  .filter(item => projectCheckAccess(item.value, AUTHORITY_TYPE.WRITABLE) || projectCheckAccess(item.value, AUTHORITY_TYPE.DELETABLE)))
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
const editFormOptions = computed(() => {
  const isWritable = projectCheckAccess(currentProject.value?.groupCode, AUTHORITY_TYPE.WRITABLE) ||
      projectCheckAccess(currentProject.value?.groupCode, AUTHORITY_TYPE.DELETABLE)
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
    labelKey: 'api.label.projectName',
    prop: 'projectName',
    required: true
  }, {
    labelKey: 'api.label.projectIcon',
    prop: 'iconUrl',
    tooltip: $i18nBundle('api.msg.projectIconTooltip'),
    slots: {
      prefix () {
        if (currentProject.value.iconUrl) {
          const iconUrl = calcProjectIconUrl(currentProject.value?.iconUrl)
          return <img src={iconUrl} class="api-project-icon-cls margin-right1" alt="logo"/>
        }
      },
      append () {
        const changeFile = ($event) => {
          uploadFiles($event.raw, (resultData) => {
            currentProject.value.iconUrl = resultData?.[0]
          })
        }
        return <ElUpload class="custom-img-upload" showFileList={false} autoUpload={false}
                         onChange={(...args) => changeFile(...args)} accept="image/*">
          <CommonIcon size={18} icon="Upload" class="append-icon-cls"/>
        </ElUpload>
      }
    }
  }, useFormStatus(), {
    enabled: !!editProjectGroupOptions.value?.length,
    labelKey: 'api.label.projectGroups',
    prop: 'groupCode',
    value: isWritable ? searchParam.value?.groupCode : '',
    type: 'select',
    children: editProjectGroupOptions.value,
    disabled: !isWritable
  }, {
    labelKey: 'common.label.description',
    prop: 'description',
    attrs: {
      type: 'textarea'
    }
  }])
})
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
    project.iconUrl = calcProjectIconUrl(project.iconUrl)
    return {
      project,
      projectItems: [{
        labelKey: 'common.label.status',
        formatter () {
          return <DelFlagTag v-model={project.status} clickToToggle={true}
                        onToggleValue={(status) => saveProjectItem({ ...project, status })} />
        }
      }, {
        labelFormatter () {
          return <ElText type="primary" tag="b">
            {$i18nBundle('api.label.projectGroups')}
          </ElText>
        },
        enabled: !!project.groupCode,
        formatter () {
          const groupOption = projectGroupOptions.value?.find(group => group.value === project.groupCode)
          return <ElText type="primary">
            {groupOption?.label || project.projectCode}
          </ElText>
        }
      }, {
        labelFormatter () {
          return <ElText type="primary" tag="b">
            {$i18nBundle('api.label.owner')}
          </ElText>
        },
        formatter () {
          return <ElText type="primary">
            {project.userName}
          </ElText>
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
    .then(() => copyProject(project.id, { loading: true }))
    .then(() => {
      ElMessage.success($i18nBundle('common.msg.operationSuccess'))
      loadApiProjects()
    })
}

const isGroupWritable = computed(() => projectCheckAccess(searchParam.value.groupCode, AUTHORITY_TYPE.WRITABLE) ||
    projectCheckAccess(searchParam.value.groupCode, AUTHORITY_TYPE.DELETABLE))

const selectedRowsDeletable = computed(() => selectedRows.value.every(project => project?.isDeletable))

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
          v-if="isGroupWritable"
          type="info"
          @click="newOrEdit()"
        >
          {{ $t('common.label.new') }}
        </el-button>
        <el-button
          v-if="selectedRows?.length&&selectedRowsDeletable"
          type="danger"
          @click="deleteProjects()"
        >
          {{ $t('common.label.delete') }}
        </el-button>
        <el-button
          v-if="isGroupWritable"
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
                <div class="flex-center-col">
                  <img
                    v-if="project.iconUrl"
                    :src="project.iconUrl"
                    class="api-project-icon-cls margin-right1"
                    alt="logo"
                  >
                  <el-text
                    tag="b"
                    :type="project.status===1?'':'danger'"
                  >
                    {{ project.projectName }}
                  </el-text>
                </div>
              </el-checkbox>
              <el-button
                v-if="project.showOperations&&project.isWritable"
                v-common-tooltip="$t('common.label.edit')"
                type="primary"
                size="small"
                round
                @click="newOrEdit(project.id, $event)"
              >
                <common-icon icon="Edit" />
              </el-button>
              <el-button
                v-if="project.showOperations&&project.isWritable"
                v-common-tooltip="$t('common.label.copy')"
                type="warning"
                size="small"
                round
                @click="toCopyProject(project, $event)"
              >
                <common-icon icon="FileCopyFilled" />
              </el-button>
              <el-button
                v-if="project.showOperations&&project.isDeletable"
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
      :default-group-code="searchParam.groupCode"
      :group-options="editProjectGroupOptions"
      @import-success="importSuccessCallback"
    />
  </el-container>
</template>

<style scoped>

</style>
