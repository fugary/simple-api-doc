<script setup lang="jsx">
import { computed, onActivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatDate, $coreConfirm, isAdminUser, checkShowColumn } from '@/utils'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useAllUsers } from '@/api/ApiUserApi'
import { AiCacheApi, getAiStatus } from '@/api/AiCacheApi'
import { showCodeWindow, showMarkdownWindow } from '@/utils/DynamicUtils'
import { $i18nBundle } from '@/messages'
import { ElText, ElTag, ElMessage } from 'element-plus'
import { useDefaultPage } from '@/config'
import { useSelectProjects } from '@/api/ApiProjectApi'
import { isJson, buildAiConfigOptions } from '@/services/api/ApiCommonService'
import { renderAiProviderTag } from '@/utils/AiProviderUtils'

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
  await router.replace({ query: { ...route.query, configId: searchParam.value.configId || undefined } })
  loadAiCaches(1)
}

const configName = item => configOptions.value.find(option => option.value === item.configId)?.label || (item.configId ? `#${item.configId}` : $i18nBundle('api.label.aiRecordUnassociated'))
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
    formatter: configName
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

const formatCodeStr = (str) => {
  if (!isJson(str)) return str
  try {
    const obj = JSON.parse(str)
    return JSON.stringify(obj, null, 2)
  } catch (e) {
    console.debug('JSON parse error in formatCodeStr:', e)
    return str
  }
}

const codeBlock = value => {
  const text = String(value)
  const fence = '`'.repeat(Math.max(3, ...[...text.matchAll(/`+/g)].map(match => match[0].length + 1)))
  return `${fence}\n${text}\n${fence}\n\n`
}

const parseJson = value => {
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

const executionSummary = value => {
  const detail = parseJson(value)
  if (!Array.isArray(detail?.steps)) return codeBlock(value)
  const steps = []
  const docs = new Map()
  detail.steps.forEach(step => {
    const args = parseJson(step.args)
    const docId = step.tool === 'get_doc' ? args?.docId : null
    const previous = docId ? docs.get(docId) : null
    if (previous) {
      previous.duration += step.duration || 0
      previous.count++
      if (previous.status !== 'failed' || step.status === 'failed') {
        previous.status = step.status
        previous.summary = step.summary || previous.summary
      }
    } else {
      const entry = { ...step, duration: step.duration || 0, count: 1, docId }
      steps.push(entry)
      if (docId) docs.set(docId, entry)
    }
  })
  let result = ''
  if (detail.finishReason) {
    const reason = {
      success: 'aiCacheStatusSuccess',
      incomplete: 'aiRecordIncomplete',
      failed: 'aiCacheStatusFailed',
      interrupted: 'aiRecordInterrupted',
      max_rounds: 'aiRecordMaxRounds',
      repeated_calls: 'aiRecordRepeatedCalls'
    }[detail.finishReason]
    result += `${$i18nBundle('api.label.aiRecordFinishReason')}\n\n${codeBlock(reason ? $i18nBundle(`api.label.${reason}`) : detail.finishReason)}`
  }
  if (detail.modelCalls != null) result += `${$i18nBundle('api.label.aiRecordModelCall')}: ${detail.modelCalls}\n\n`
  if (detail.toolCalls != null) result += `${$i18nBundle('api.label.aiRecordTool')}: ${detail.toolCalls}\n\n`
  if (detail.truncated) result += `${$i18nBundle('api.label.aiRecordTruncated')}\n\n`
  if (detail.usageComplete === false) result += `${$i18nBundle('api.label.aiRecordPartialUsage')}\n\n`
  steps.forEach((step, index) => {
    const label = {
      model: 'aiRecordModelCall',
      overview: 'aiRecordOverview',
      search_docs: 'aiRecordSearch',
      get_doc: 'aiRecordRead'
    }[step.tool || step.type]
    const status = { success: 'aiCacheStatusSuccess', failed: 'aiCacheStatusFailed', skipped: 'aiRecordSkipped' }[step.status]
    result += `#### ${index + 1}. ${label ? $i18nBundle(`api.label.${label}`) : $i18nBundle('api.label.aiRecordTool')}\n\n`
    const lines = [
      `${$i18nBundle('api.label.aiCacheStatus')}: ${status ? $i18nBundle(`api.label.${status}`) : step.status || '-'}`,
      `${$i18nBundle('api.label.aiCacheCostTime')}: ${step.duration}`
    ]
    if (step.docId) lines.push(`${$i18nBundle('api.label.aiRecordRead')}: #${step.docId} (${step.count})`)
    else if (step.args) lines.push(formatCodeStr(step.args))
    if (step.summary) lines.push(step.summary)
    result += codeBlock(lines.join('\n'))
  })
  return result
}

const showCacheDetail = async row => {
  const res = await AiCacheApi.getById(row.cacheKey)
  if (!res.success || !res.resultData) return
  const item = res.resultData
  const metadata = [
    ['aiConfigSelect', configName(item)], ['provider', item.provider || '-'],
    ['aiCacheModelName', item.modelName || '-'], ['aiRecordSource', accessSource(item)]
  ]
  let detailStr = codeBlock(metadata.map(([label, value]) => `${$i18nBundle(`api.label.${label}`)}: ${value}`).join('\n'))
  const sections = [
    ['aiCachePrompt', item.prompt],
    ['aiCacheValue', item.cacheValue],
    ['aiCacheErrorMessage', item.errorMessage]
  ]
  sections.forEach(([label, value]) => {
    if (value) detailStr += `### ${$i18nBundle(`api.label.${label}`)}\n\n${codeBlock(formatCodeStr(value))}`
  })
  if (item.executionDetail) detailStr += `### ${$i18nBundle('api.label.aiRecordExecution')}\n\n${executionSummary(item.executionDetail)}`
  if (item.rawResponse) detailStr += `### ${$i18nBundle('api.label.aiCacheRawResponse')}\n\n${codeBlock(formatCodeStr(item.rawResponse))}`
  showMarkdownWindow({
    content: detailStr,
    title: $i18nBundle('api.label.aiCacheManagement'),
    previewOnly: true
  })
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
  </el-container>
</template>

<style scoped>

</style>
