<script setup lang="jsx">
import { computed, onActivated, onMounted, ref } from 'vue'
import { formatDate, $coreConfirm, isAdminUser, checkShowColumn } from '@/utils'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useAllUsers } from '@/api/ApiUserApi'
import { AiCacheApi } from '@/api/AiCacheApi'
import { showCodeWindow, showMarkdownWindow } from '@/utils/DynamicUtils'
import { $i18nBundle } from '@/messages'
import { ElText, ElTag, ElMessage } from 'element-plus'
import { useDefaultPage } from '@/config'
import { useSelectProjects } from '@/api/ApiProjectApi'

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
  if (isAdminUser()) {
    await loadUsersAndRefreshOptions(false)
  }
  await loadProjectsAndRefreshOptions()
  await loadAiCaches()
})

onMounted(initLoadOnce)

onActivated(initLoadOnce)

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
    labelKey: 'api.label.provider',
    prop: 'provider',
    minWidth: '100px',
    formatter (data) {
      if (!data.provider) return ''
      if (data.provider === 'OPENAI') return <ElTag type="success" disable-transitions>OpenAI</ElTag>
      if (data.provider === 'ANTHROPIC') return <ElTag type="warning" disable-transitions>Anthropic</ElTag>
      if (data.provider === 'GEMINI') return <ElTag type="primary" disable-transitions>Gemini</ElTag>
      return <ElTag type="info" disable-transitions>{data.provider}</ElTag>
    }
  }, {
    labelKey: 'api.label.aiCacheModelName',
    minWidth: '150px',
    prop: 'modelName'
  }, {
    labelKey: 'api.label.aiCacheType',
    prop: 'cacheType',
    formatter (data) {
      if (data.cacheType === 'mock_data') return $i18nBundle('api.label.aiCacheTypeMockData')
      if (data.cacheType === 'generate_desc') return $i18nBundle('api.label.aiCacheTypeGenerateDesc')
      if (data.cacheType === 'test_config') return $i18nBundle('common.label.test')
      return data.cacheType || ''
    }
  }, {
    label: 'Tokens',
    prop: 'totalTokens'
  }, {
    labelKey: 'api.label.aiCacheStatus',
    formatter (data) {
      if (data.status === 1) {
        return <ElTag type="success" disable-transitions>{ $i18nBundle('api.label.aiCacheStatusSuccess') }</ElTag>
      } else if (data.status === 2) {
        return <ElTag type="danger" disable-transitions>{ $i18nBundle('api.label.aiCacheStatusFailed') }</ElTag>
      }
      return <ElTag type="info" disable-transitions>{ $i18nBundle('api.label.aiCacheStatusPending') }</ElTag>
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
      return <ElText onClick={() => showCodeWindow(dataStr)}
                     style="white-space: nowrap;cursor: pointer; max-width: 300px" truncated>
        {dataStr}
      </ElText>
    }
  }]
})

const showCacheDetail = item => {
  let detailStr = ''
  if (item.prompt) {
    detailStr += `### ${$i18nBundle('api.label.aiCachePrompt')}\n\`\`\`\n${item.prompt}\n\`\`\`\n\n`
  }
  if (item.rawResponse) {
    detailStr += `### ${$i18nBundle('api.label.aiCacheRawResponse')}\n\`\`\`json\n${item.rawResponse}\n\`\`\`\n\n`
  }
  if (item.cacheValue) {
    detailStr += `### ${$i18nBundle('api.label.aiCacheValue')}\n\`\`\`json\n${item.cacheValue}\n\`\`\`\n\n`
  }
  if (item.errorMessage) {
    detailStr += `### ${$i18nBundle('api.label.aiCacheErrorMessage')}\n\`\`\`\n${item.errorMessage}\n\`\`\`\n\n`
  }
  showMarkdownWindow({
    content: detailStr,
    title: $i18nBundle('common.label.info'),
    previewOnly: true
  })
}

const removeCache = async (item) => {
  await $coreConfirm($i18nBundle('common.msg.deleteConfirm'))
  await AiCacheApi.deleteById(item.cacheKey)
  ElMessage.success($i18nBundle('common.msg.deleteSuccess'))
  loadAiCaches()
}

const showJsonDetail = item => {
  showCodeWindow(JSON.stringify(item, null, 2), { language: 'json' })
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
    labelKey: 'api.label.aiCacheModelName',
    prop: 'modelName'
  }, {
    labelKey: 'api.label.aiCacheType',
    prop: 'cacheType',
    type: 'select',
    children: [
      { labelKey: 'api.label.aiCacheTypeMockData', value: 'mock_data' },
      { labelKey: 'api.label.aiCacheTypeGenerateDesc', value: 'generate_desc' },
      { labelKey: 'common.label.test', value: 'test_config' }
    ],
    change () {
      loadAiCaches()
    }
  }, {
    labelKey: 'api.label.aiCacheStatus',
    prop: 'status',
    type: 'select',
    children: [
      { labelKey: 'api.label.aiCacheStatusPending', value: 0 },
      { labelKey: 'api.label.aiCacheStatusSuccess', value: 1 },
      { labelKey: 'api.label.aiCacheStatusFailed', value: 2 }
    ],
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
      :buttons-column-attrs="{minWidth:'220px'}"
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
