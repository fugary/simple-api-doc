<script setup lang="jsx">
import { ref, onMounted, onActivated, computed, useTemplateRef } from 'vue'
import { useRoute } from 'vue-router'
import { useBackUrl } from '@/utils'
import { useApiProjectItem } from '@/api/ApiProjectApi'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useDefaultPage } from '@/config'
import ApiProjectInfoDetailApi from '@/api/ApiProjectInfoDetailApi'
import { inProjectCheckAccess } from '@/api/ApiProjectGroupApi'
import { AUTHORITY_TYPE } from '@/consts/ApiConstants'
import ApiProjectComponent from '@/views/components/api/project/ApiProjectComponent.vue'
import { ElMessage } from 'element-plus'
import { $i18nBundle } from '@/messages'

const route = useRoute()
const projectCode = route.params.projectCode
const componentsTableRef = useTemplateRef('componentsTableRef')

const { goBack } = useBackUrl(`/api/projects/${projectCode}`)
const { projectItem, loadProjectItem } = useApiProjectItem(projectCode, { autoLoad: false, detail: false })
const currentInfoDetail = ref(null)

const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { keyword: '', page: useDefaultPage(15), bodyType: 'component' },
  searchMethod: ApiProjectInfoDetailApi.search
})
const loadProjectComponents = (pageNumber) => searchMethod(pageNumber).then(data => {
  if (data.resultData?.length) {
    if (!currentInfoDetail.value) {
      currentInfoDetail.value = data.resultData[0]
    } else {
      console.log('=========================', data.resultData, currentInfoDetail.value)
      currentInfoDetail.value = data.resultData.find(item => item.id === currentInfoDetail.value.id) || data.resultData[0]
    }
    componentsTableRef.value?.table?.setCurrentRow(currentInfoDetail.value)
  }
  return data
})

const newOrEdit = (id) => {
  if (id) {
    ApiProjectInfoDetailApi.getById(id).then(data => {
      currentInfoDetail.value = data.resultData
    })
  } else {
    console.log('=========================new', id)
    currentInfoDetail.value = {
      bodyType: 'component',
      schemaContent: '{}',
      projectId: projectItem.value?.id,
      infoId: projectItem.value?.infoList?.[0]?.id
    }
  }
}

const { initLoadOnce } = useInitLoadOnce(async () => {
  await loadProjectItem(projectCode)
  searchParam.value.projectId = projectItem.value?.id
  await loadProjectComponents()
})

onMounted(initLoadOnce)

onActivated(initLoadOnce)

const searchFormOptions = computed(() => {
  return [{
    labelKey: 'common.label.keywords',
    prop: 'keyword'
  }]
})
const columns = [{
  headerSlot: 'buttonHeader',
  labelKey: 'api.label.dataModel',
  prop: 'schemaName'
}]

const pageAttrs = {
  layout: 'prev, pager, next',
  background: true,
  hideOnSinglePage: true,
  pagerCount: 5
}

const splitSizes = ref([25, 75])
const defaultMinSizes = ref([200, 500])

const isDeletable = computed(() => inProjectCheckAccess(projectItem.value, AUTHORITY_TYPE.DELETABLE))
const isWritable = computed(() => inProjectCheckAccess(projectItem.value, AUTHORITY_TYPE.WRITABLE) || isDeletable.value)

const projectInfos = computed(() => {
  if (projectItem.value?.infoList?.length) {
    return projectItem.value.infoList.map(info => {
      if (info.folderId && projectItem.value.folders?.length) {
        info.folderName = projectItem.value.folders.find(item => item.id === info.folderId)?.folderName
      }
      return info
    })
  }
  return []
})
const saveComponent = (data) => {
  ApiProjectInfoDetailApi.saveOrUpdate(data)
    .then((data) => {
      if (data.success) {
        ElMessage.success($i18nBundle('common.msg.saveSuccess'))
        currentInfoDetail.value = data.resultData
      }
      loadProjectComponents()
    })
}
const deleteComponent = (data) => {
  ApiProjectInfoDetailApi.deleteById(data.id)
    .then(() => {
      loadProjectComponents()
    })
}

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
            {{ projectItem?.projectName }} - {{ $t('api.label.dataModel') }}
          </span>
        </el-container>
      </template>
    </el-page-header>
    <el-container
      v-loading="loading"
      style="height: calc(100% - 60px);"
    >
      <div class="form-edit-width-100">
        <common-form
          inline
          :model="searchParam"
          :options="searchFormOptions"
          :back-url="goBack"
          :submit-label="$t('common.label.search')"
          @submit-form="loadProjectComponents"
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
        <common-split
          v-if="projectItem"
          :sizes="splitSizes"
          :min-size="defaultMinSizes"
          class="height100"
        >
          <template #split-0>
            <common-table
              ref="componentsTableRef"
              v-model:page="searchParam.page"
              class="request-table"
              :data="tableData"
              :buttons-column-attrs="{minWidth:'100px'}"
              :columns="columns"
              :loading="loading"
              :page-attrs="pageAttrs"
              @page-size-change="loadProjectComponents()"
              @current-page-change="loadProjectComponents()"
              @current-change="$event?newOrEdit($event?.id):undefined"
            >
              <template #buttonHeader>
                {{ $t('api.label.dataModel') }}
                <el-tag
                  v-if="searchParam.page?.totalCount"
                  :title="$t('api.label.dataModel')"
                  class="margin-left1 pointer"
                  type="primary"
                  size="small"
                  effect="plain"
                  round
                >
                  {{ searchParam.page?.totalCount }}
                </el-tag>
              </template>
            </common-table>
          </template>
          <template #split-1>
            <api-project-component
              v-if="currentInfoDetail"
              v-model="currentInfoDetail"
              :project-infos="projectInfos"
              :writable="isWritable"
              :deletable="isDeletable"
              @save-component="saveComponent"
              @delete-component="deleteComponent"
            />
          </template>
        </common-split>
      </div>
    </el-container>
  </el-container>
</template>

<style scoped>

</style>
