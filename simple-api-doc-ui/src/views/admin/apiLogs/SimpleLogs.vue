<script setup lang="jsx">
import { computed, onActivated, onMounted, ref } from 'vue'
import { checkShowColumn, formatDate, getSingleSelectOptions, isAdminUser } from '@/utils'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useAllUsers } from '@/api/ApiUserApi'
import ApiLogApi from '@/api/ApiLogApi'
import { showCodeWindow } from '@/utils/DynamicUtils'
import { ElText, ElTag } from 'element-plus'
import { useDefaultPage } from '@/config'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'

const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { keyword: '', page: useDefaultPage() },
  searchMethod: ApiLogApi.search
})

const loadApiLogs = (...args) => {
  searchParam.value.startDate = formatDate(dateParam.value?.createDates?.[0])
  searchParam.value.endDate = formatDate(dateParam.value?.createDates?.[1])
  return searchMethod(...args)
}
const dateParam = ref({
  createDates: []
})
const { userOptions, loadUsersAndRefreshOptions } = useAllUsers(searchParam)

const { initLoadOnce } = useInitLoadOnce(async () => {
  await loadUsersAndRefreshOptions(false)
  await loadApiLogs()
})

onMounted(initLoadOnce)

onActivated(initLoadOnce)

const columns = computed(() => {
  return [{
    labelKey: 'common.label.user',
    prop: 'creator',
    enabled: checkShowColumn(tableData.value, 'creator')
  }, {
    labelKey: 'api.label.logName',
    prop: 'logName',
    minWidth: '150px'
  }, {
    labelKey: 'api.label.logResult',
    formatter (data) {
      if (data.logResult) {
        const type = data.logResult === 'SUCCESS' ? 'success' : 'danger'
        return <ElTag type={type}>{data.logResult}</ElTag>
      }
    }
  }, {
    labelKey: 'api.label.logTime',
    prop: 'logTime'
  }, {
    labelKey: 'api.label.logType',
    formatter (data) {
      if (data.logType) {
        return <ApiMethodTag size="default" method={data.logType}/>
      }
    }
  }, {
    labelKey: 'api.label.ipAddress',
    prop: 'ipAddress'
  }, {
    labelKey: 'api.label.logMessage',
    prop: 'logMessage'
  }, {
    labelKey: 'api.label.logData',
    minWidth: '150px',
    formatter (data) {
      const dataStr = data.logData || data.responseBody
      return <ElText onClick={() => showCodeWindow(dataStr)}
                     style="white-space: nowrap;cursor: pointer;">
        {dataStr}
      </ElText>
    }
  }, {
    labelKey: 'api.label.exceptions',
    enabled: checkShowColumn(tableData.value, 'exceptions'),
    formatter (data) {
      return <ElText onClick={() => showCodeWindow(data.exceptions)}
                     style="white-space: nowrap;cursor: pointer;">
        {data.exceptions}
      </ElText>
    }
  }, {
    labelKey: 'common.label.createDate',
    property: 'createDate',
    dateFormat: 'YYYY-MM-DD HH:mm:ss'
  }]
})

const buttons = computed(() => {
  return [{
    labelKey: 'common.label.view',
    type: 'primary',
    click: item => {
      showCodeWindow(JSON.stringify(item))
    }
  }]
})
//* ************搜索框**************//
const searchFormOptions = computed(() => {
  return [{
    labelKey: 'common.label.user',
    prop: 'userName',
    type: 'select',
    enabled: isAdminUser(),
    children: userOptions.value,
    change () {
      loadApiLogs()
    }
  }, {
    labelKey: 'api.label.logName',
    prop: 'logName'
  }, {
    labelKey: 'api.label.logResult',
    prop: 'logResult',
    type: 'select',
    children: getSingleSelectOptions('SUCCESS', 'FAIL')
  }, {
    labelKey: 'api.label.logType',
    prop: 'logType'
  }, {
    labelKey: 'api.label.ipAddress',
    prop: 'ipAddress'
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
      @submit-form="loadApiLogs()"
    />
    <common-table
      v-model:page="searchParam.page"
      :data="tableData"
      :buttons-column-attrs="{minWidth:'100px'}"
      :columns="columns"
      :buttons="buttons"
      :loading="loading"
      @page-size-change="loadApiLogs()"
      @current-page-change="loadApiLogs()"
    />
  </el-container>
</template>

<style scoped>

</style>
