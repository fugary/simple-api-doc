<script setup lang="jsx">
import { useRoute } from 'vue-router'
import { $coreConfirm, useBackUrl } from '@/utils'
import { useTableAndSearchForm } from '@/hooks/CommonHooks'
import { defineFormOptions } from '@/components/utils'
import { ref, computed, nextTick } from 'vue'
import { useFormDelay, useFormStatus } from '@/consts/GlobalConstants'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import { useDefaultPage } from '@/config'
import { $i18nBundle } from '@/messages'
import { useMonacoEditorOptions } from '@/vendors/monaco-editor'
import CommonIcon from '@/components/common-icon/index.vue'
import { useApiProjectItem } from '@/api/ApiProjectApi'
import ApiProjectResourceApi from '@/api/ApiProjectResourcesApi'
import ApiProjectImport from '@/views/components/api/ApiProjectImport.vue'

const route = useRoute()
const projectCode = route.params.projectCode

const { goBack } = useBackUrl('/api/projects')
const { projectItem, loadSuccess } = useApiProjectItem(projectCode)

const { tableData, loading, searchParam, searchMethod: searchMockRequests } = useTableAndSearchForm({
  defaultParam: { projectCode, page: useDefaultPage(50) },
  searchMethod: (param) => ApiProjectResourceApi.search(param, { loading: true }),
  saveParam: false
})

const loadMockRequests = (...args) => {
  return searchMockRequests(...args).then((result) => {
    nextTick(() => {
      if (tableData.value?.length) {
        selectRequest.value = tableData.value.find(req => req.id === selectRequest.value?.id) || tableData.value[0]
        requestTableRef.value?.table?.setCurrentRow(selectRequest.value, true)
      }
    })
    return result
  })
}

loadMockRequests()

//* ************搜索框**************//
const searchFormOptions = computed(() => {
  return [
    {
      labelKey: 'common.label.keywords',
      prop: 'keyword'
    }
  ]
})
const selectedRows = ref([])
const requestTableRef = ref()

const deleteRequests = () => {
  $coreConfirm($i18nBundle('common.msg.deleteConfirm'))
    .then(() => ApiProjectResourceApi.removeByIds(selectedRows.value.map(item => item.id)), { loading: true })
    .then(() => loadMockRequests())
}

const newRequestItem = () => ({
  status: 1,
  method: 'GET',
  projectCode
})
const showEditWindow = ref(false)
const currentRequest = ref(newRequestItem())
const selectRequest = ref()
const newOrEdit = async id => {
  if (id) {
    await ApiProjectResourceApi.getById(id).then(data => {
      data.resultData && (currentRequest.value = data.resultData)
    })
  } else {
    currentRequest.value = newRequestItem()
  }
  showEditWindow.value = true
}
const { contentRef, languageRef, monacoEditorOptions } = useMonacoEditorOptions({
  readOnly: false,
  lineNumbers: 'off',
  minimap: { enabled: false }
})
const editFormOptions = computed(() => {
  return defineFormOptions([{
    labelKey: 'api.label.requestPath',
    prop: 'requestPath',
    required: true,
    change (val) {
      if (val && !val.startsWith('/')) {
        currentRequest.value.requestPath = `/${val.trim()}`
      }
    }
  }, useFormStatus(), useFormDelay(),
  {
    labelKey: 'api.label.matchPattern',
    type: 'vue-monaco-editor',
    prop: 'matchPattern',
    tooltip: $i18nBundle('api.msg.matchPatternTooltip'),
    attrs: {
      class: 'common-resize-vertical',
      value: currentRequest.value?.matchPattern,
      'onUpdate:value': (value) => {
        currentRequest.value.matchPattern = value
        contentRef.value = value
        languageRef.value = 'javascript'
      },
      language: languageRef.value,
      height: '100px',
      options: monacoEditorOptions
    }
  }, {
    labelKey: 'api.label.requestName',
    prop: 'requestName',
    tooltip: $i18nBundle('api.msg.requestNameTooltip')
  }, {
    labelKey: 'common.label.description',
    prop: 'description',
    attrs: {
      type: 'textarea'
    }
  }])
})

const saveMockRequest = item => {
  return ApiProjectResourceApi.saveOrUpdate(item)
    .then(() => loadMockRequests())
}

const batchMode = ref(false)

const newColumns = computed(() => {
  return [{
    width: '50px',
    enabled: batchMode.value,
    attrs: {
      type: 'selection'
    }
  }, {
    headerSlot: 'buttonHeader',
    property: 'requestPath',
    formatter (data) {
      return data.id
    },
    minWidth: '150px'
  }]
})

const changeBatchMode = () => {
  batchMode.value = !batchMode.value
  if (!batchMode.value) {
    selectedRows.value = []
  }
}

const showImportWindow = ref(false)

</script>

<template>
  <el-container class="flex-column">
    <el-page-header @back="goBack">
      <template #content>
        <span class="text-large font-600 mr-3">
          {{ projectItem?.projectName }} 【{{ projectItem?.projectCode }}】
        </span>
      </template>
    </el-page-header>
    <common-form
      class="margin-top3"
      inline
      :model="searchParam"
      :options="searchFormOptions"
      :submit-label="$t('common.label.search')"
      @submit-form="loadMockRequests()"
    >
      <template #buttons>
        <el-button
          type="success"
          @click="showImportWindow = true"
        >
          {{ $t('api.label.import') }}
        </el-button>
        <el-button
          @click="goBack()"
        >
          {{ $t('common.label.back') }}
        </el-button>
      </template>
    </common-form>
    <el-container v-if="loadSuccess">
      <div class="form-edit-width-100">
        <common-split
          :min-size="150"
          :max-size="[500, Infinity]"
        >
          <template #split-0>
            <div class="padding-right2">
              <common-table
                :key="batchMode"
                ref="requestTableRef"
                class="request-table"
                :data="tableData"
                :columns="newColumns"
                :loading="loading"
                row-key="id"
                @selection-change="selectedRows=$event"
                @current-change="selectRequest=$event"
              >
                <template #buttonHeader>
                  {{ $t('api.label.interfaces') }}
                  <div class="float-right">
                    <el-button
                      v-common-tooltip="$t('common.label.batchMode')"
                      round
                      :type="batchMode?'success':'default'"
                      size="small"
                      @click="changeBatchMode"
                    >
                      <common-icon :icon="batchMode?'LibraryAddCheckFilled':'LibraryAddCheckOutlined'" />
                    </el-button>
                    <el-button
                      v-common-tooltip="$t('common.label.new')"
                      round
                      type="primary"
                      size="small"
                      @click="newOrEdit()"
                    >
                      <common-icon icon="Plus" />
                    </el-button>
                    <el-button
                      v-if="selectedRows.length"
                      v-common-tooltip="$t('common.label.delete')"
                      round
                      type="danger"
                      size="small"
                      @click="deleteRequests()"
                    >
                      <common-icon icon="DeleteFilled" />
                    </el-button>
                  </div>
                </template>
              </common-table>
            </div>
          </template>
          <template #split-1>
            111
          </template>
        </common-split>
      </div>
    </el-container>
    <simple-edit-window
      v-model="currentRequest"
      v-model:show-edit-window="showEditWindow"
      :form-options="editFormOptions"
      :name="$t('api.label.interfaces')"
      :save-current-item="saveMockRequest"
      label-width="130px"
    />
    <api-project-import v-model="showImportWindow" />
  </el-container>
</template>

<style scoped>

</style>
