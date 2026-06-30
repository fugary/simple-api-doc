<script setup lang="jsx">
import { computed, onActivated, onMounted, ref } from 'vue'
import { formatDate, $coreConfirm } from '@/utils'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import AiCacheApi from '@/api/AiCacheApi'
import { showCodeWindow, showMarkdownWindow } from '@/utils/DynamicUtils'
import { $i18nBundle } from '@/messages'
import { ElText, ElTag, ElMessage } from 'element-plus'
import { useDefaultPage } from '@/config'

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

const { initLoadOnce } = useInitLoadOnce(async () => {
  await loadAiCaches()
})

onMounted(initLoadOnce)

onActivated(initLoadOnce)

const columns = computed(() => {
  return [{
    labelKey: 'api.label.aiCacheKey',
    prop: 'cacheKey',
    minWidth: '150px',
    enabled: false
  }, {
    labelKey: 'api.label.aiCacheModelName',
    minWidth: '150px',
    prop: 'modelName'
  }, {
    labelKey: 'api.label.projectId',
    label: '项目ID',
    prop: 'projectId'
  }, {
    labelKey: 'api.label.userName',
    label: '操作用户',
    prop: 'userName'
  }, {
    labelKey: 'api.label.totalTokens',
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
    label: '完成时间',
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
  if (item.cacheValue) {
    detailStr += `### ${$i18nBundle('api.label.aiCacheValue')}\n\`\`\`json\n${item.cacheValue}\n\`\`\`\n\n`
  }
  if (item.rawResponse) {
    detailStr += `### ${$i18nBundle('api.label.aiCacheRawResponse')}\n\`\`\`json\n${item.rawResponse}\n\`\`\`\n\n`
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

const buttons = computed(() => {
  return [{
    labelKey: 'common.label.view',
    type: 'primary',
    click: showCacheDetail
  }, {
    labelKey: 'common.label.delete',
    type: 'danger',
    click: removeCache
  }]
})

// ************搜索框**************//
const searchFormOptions = computed(() => {
  return [{
    labelKey: 'api.label.aiCacheModelName',
    prop: 'modelName'
  }, {
    label: '项目ID',
    prop: 'projectId'
  }, {
    label: '操作用户',
    prop: 'userName'
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
      :buttons-column-attrs="{minWidth:'150px'}"
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
