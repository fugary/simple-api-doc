<script setup lang="jsx">
import { computed, onActivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatDate, $coreConfirm, isAdminUser, checkShowColumn } from '@/utils'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useAllUsers } from '@/api/ApiUserApi'
import { AiCacheApi, getAiStatus } from '@/api/AiCacheApi'
import { showCodeWindow } from '@/utils/DynamicUtils'
import { $i18nBundle } from '@/messages'
import { ElText, ElTag, ElMessage } from 'element-plus'
import { useDefaultPage } from '@/config'
import { useSelectProjects } from '@/api/ApiProjectApi'
import { buildAiConfigOptions } from '@/services/api/ApiCommonService'
import { renderAiProviderTag } from '@/utils/AiProviderUtils'
import AiRecordDetailWindow from './AiRecordDetailWindow.vue'

const route = useRoute()
const router = useRouter()
const aiConfigs = ref([])
const configOptions = computed(() => {
  const options = buildAiConfigOptions(aiConfigs.value)
  const ids = [searchParam.value.configId, ...tableData.value.map(item => item.configId)]
  ids.filter(Boolean).forEach(id => {
    if (!options.some(option => option.value === id)) options.push({ label: `#${id}`, value: id })
  })
  return options
})
const typeOptions = [
  { labelKey: 'api.label.aiCacheTypeMockData', value: 'mock_data' },
  { labelKey: 'api.label.aiCacheTypeGenerateDesc', value: 'generate_desc' },
  { labelKey: 'api.label.aiCacheTypeGenerateModel', value: 'generate_model' },
  { labelKey: 'common.label.test', value: 'test_config' },
  { labelKey: 'api.label.aiRecordAgentChat', value: 'agent_chat' }
]
const statusOptions = [
  { labelKey: 'api.label.aiCacheStatusPending', value: 0, type: 'info' },
  { labelKey: 'api.label.aiCacheStatusSuccess', value: 1, type: 'success' },
  { labelKey: 'api.label.aiCacheStatusFailed', value: 2, type: 'danger' },
  { labelKey: 'api.label.aiRecordIncomplete', value: 3, type: 'warning' },
  { labelKey: 'api.label.aiRecordInterrupted', value: 4, type: 'info' }
]

const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { keyword: '', page: useDefaultPage() },
  searchMethod: AiCacheApi.search
})

const loadAiCaches = (...args) => {
  searchParam.value.startDate = formatDate(dateParam.value?.createDates?.[0])
  searchParam.value.endDate = formatDate(dateParam.value?.createDates?.[1])
  return searchMethod(...args)
}
const dateParam = ref({
  createDates: []
})

const { userOptions, loadUsersAndRefreshOptions } = useAllUsers(searchParam)
const { projectOptions, loadProjectsAndRefreshOptions } = useSelectProjects(searchParam)

const { initLoadOnce } = useInitLoadOnce(async () => {
  syncConfigFromRoute()
  if (isAdminUser()) {
    await loadUsersAndRefreshOptions(false)
  }
  await loadProjectsAndRefreshOptions()
  const res = await getAiStatus()
  aiConfigs.value = res.success ? res.resultData?.configs || [] : []
  await loadAiCaches()
})

onMounted(initLoadOnce)

onActivated(initLoadOnce)

function syncConfigFromRoute () {
  const value = Number(route.query.configId)
  const configId = Number.isSafeInteger(value) && value > 0 ? value : undefined
  if (searchParam.value.configId === configId) return false
  searchParam.value.configId = configId
  searchParam.value.page.pageNumber = 1
  return true
}

watch(() => route.query.configId, () => {
  if (route.name === 'AiCaches' && syncConfigFromRoute()) loadAiCaches()
})

const changeConfig = async () => {
  searchParam.value.configId = searchParam.value.configId || undefined
  searchParam.value.page.pageNumber = 1
  // 地址变化会触发页面初始化，由初始化统一查询，避免重复请求。
  const query = { ...route.query, configId: searchParam.value.configId }
  if (router.resolve({ query }).fullPath === route.fullPath) {
    return loadAiCaches(1)
  }
  await router.replace({ query })
}

const configOption = item => configOptions.value.find(option => option.value === item.configId) || { label: item.configId ? `#${item.configId}` : $i18nBundle('api.label.aiRecordUnassociated') }
const accessSource = item => item.shareRecordId ? `${$i18nBundle('api.label.aiRecordShare')} #${item.shareRecordId}` : $i18nBundle('api.label.aiRecordDirect')

const columns = computed(() => {
  return [{
    labelKey: 'common.label.user',
    prop: 'userName'
  }, {
    labelKey: 'api.label.project',
    prop: 'projectId',
    enabled: checkShowColumn(tableData.value, 'projectId'),
    formatter (data) {
      return projectOptions.value.find(project => `${project.value}` === `${data.projectId}`)?.label || data.projectId
    }
  }, {
    labelKey: 'api.label.aiConfigSelect',
    prop: 'configId',
    minWidth: '160px',
    formatter: item => configOption(item).slots?.default() || configOption(item).label
  }, {
    labelKey: 'api.label.aiRecordSource',
    prop: 'shareRecordId',
    formatter: accessSource
  }, {
    labelKey: 'api.label.provider',
    prop: 'provider',
    minWidth: '100px',
    formatter (data) {
      return renderAiProviderTag(data.provider)
    }
  }, {
    labelKey: 'api.label.aiCacheModelName',
    minWidth: '150px',
    prop: 'modelName'
  }, {
    labelKey: 'api.label.aiCacheType',
    prop: 'cacheType',
    formatter (data) {
      const option = typeOptions.find(option => option.value === data.cacheType)
      return option ? $i18nBundle(option.labelKey) : data.cacheType || ''
    }
  }, {
    label: 'Tokens',
    prop: 'totalTokens'
  }, {
    labelKey: 'api.label.aiCacheStatus',
    formatter (data) {
      const option = statusOptions.find(option => option.value === data.status) || statusOptions[0]
      return <ElTag type={option.type} disable-transitions>{ $i18nBundle(option.labelKey) }</ElTag>
    }
  }, {
    labelKey: 'api.label.aiCacheCostTime',
    prop: 'costTime'
  }, {
    labelKey: 'common.label.createDate',
    property: 'createdAt',
    dateFormat: 'YYYY-MM-DD HH:mm:ss'
  }, {
    labelKey: 'common.label.modifyDate',
    property: 'updatedAt',
    dateFormat: 'YYYY-MM-DD HH:mm:ss'
  }, {
    labelKey: 'api.label.aiCacheValue',
    minWidth: '150px',
    formatter (data) {
      const dataStr = data.cacheValue
      if (!dataStr) return ''
      return <ElText onClick={() => showCacheDetail(data)}
                     style="white-space: nowrap;cursor: pointer; max-width: 300px" truncated>
        {dataStr}
      </ElText>
    }
  }]
})

const detailRecord = ref(null)
const showDetail = ref(false)
const showCacheDetail = async row => {
  const res = await AiCacheApi.getById(row.cacheKey)
  if (!res.success || !res.resultData) return
  detailRecord.value = res.resultData
  showDetail.value = true
}

const removeCache = async (item) => {
  if (item.status === 0) return
  const generation = ['mock_data', 'generate_desc', 'generate_model'].includes(item.cacheType)
  await $coreConfirm($i18nBundle(generation ? 'api.msg.deleteAiGenerationRecord' : 'api.msg.deleteAiRecord'))
  const res = await AiCacheApi.deleteById(item.cacheKey)
  if (!res.success) return
  ElMessage.success($i18nBundle('common.msg.deleteSuccess'))
  loadAiCaches()
}

const showJsonDetail = async item => {
  const res = await AiCacheApi.getById(item.cacheKey)
  if (res.success) showCodeWindow(JSON.stringify(res.resultData, null, 2), { language: 'json' })
}

const buttons = computed(() => {
  return [{
    labelKey: 'common.label.view',
    type: 'primary',
    click: showCacheDetail
  }, {
    label: 'JSON',
    type: 'info',
    click: showJsonDetail
  }, {
    labelKey: 'common.label.delete',
    type: 'danger',
    dynamicButton: item => ({ disabled: item.status === 0 }),
    click: removeCache
  }]
})

// ************搜索框**************//
const searchFormOptions = computed(() => {
  return [{
    labelKey: 'common.label.user',
    prop: 'userName',
    type: 'select',
    enabled: isAdminUser(),
    children: userOptions.value,
    change () {
      loadAiCaches()
    }
  }, {
    labelKey: 'api.label.project',
    prop: 'projectId',
    type: 'select',
    enabled: !!projectOptions.value.length,
    children: projectOptions.value,
    change () {
      loadAiCaches()
    }
  }, {
    labelKey: 'api.label.aiConfigSelect',
    prop: 'configId',
    type: 'select',
    children: configOptions.value,
    attrs: { clearable: true, filterable: true },
    change: changeConfig
  }, {
    labelKey: 'api.label.aiCacheModelName',
    prop: 'modelName'
  }, {
    labelKey: 'api.label.aiCacheType',
    prop: 'cacheType',
    type: 'select',
    children: typeOptions,
    change () {
      loadAiCaches()
    }
  }, {
    labelKey: 'api.label.aiCacheStatus',
    prop: 'status',
    type: 'select',
    children: statusOptions,
    change () {
      loadAiCaches()
    }
  }, {
    model: dateParam.value,
    labelKey: 'common.label.createDate',
    prop: 'createDates',
    type: 'date-picker',
    attrs: {
      type: 'datetimerange',
      unlinkPanels: true
    }
  }, {
    labelKey: 'common.label.keywords',
    prop: 'keyword'
  }]
})

</script>

<template>
  <el-container class="flex-column">
    <common-form
      inline
      :model="searchParam"
      :options="searchFormOptions"
      :submit-label="$t('common.label.search')"
      @submit-form="loadAiCaches()"
    />
    <common-table
      v-model:page="searchParam.page"
      :data="tableData"
      :buttons-column-attrs="{minWidth:'220px', fixed: 'right'}"
      :columns="columns"
      :buttons="buttons"
      :loading="loading"
      @page-size-change="loadAiCaches()"
      @current-page-change="loadAiCaches()"
      @row-dblclick="showCacheDetail($event)"
    />
    <ai-record-detail-window
      v-if="showDetail && detailRecord"
      v-model="showDetail"
      :record="detailRecord"
      :config-option="configOption(detailRecord)"
      :status-option="statusOptions.find(item => item.value === detailRecord.status) || statusOptions[0]"
    />
  </el-container>
</template>

<style scoped>

</style>
