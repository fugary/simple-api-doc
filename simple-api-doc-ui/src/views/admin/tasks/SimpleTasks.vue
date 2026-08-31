<script setup lang="jsx">
import { computed, onActivated, onMounted } from 'vue'
import { $coreAlert, $coreConfirm, $goto, isAdminUser, checkShowColumn } from '@/utils'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import SimpleTaskApi, { removeAndDisable, triggerSimpleTask } from '@/api/SimpleTaskApi'
import { $i18nBundle, $i18nKey } from '@/messages'
import { TASK_STATUS_MAPPING } from '@/consts/ApiConstants'
import { ElTag, ElText, ElTooltip } from 'element-plus'
import dayjs from 'dayjs'
import { useAllUsers } from '@/api/ApiUserApi'
import { useRoute } from 'vue-router'
import { showTaskLogsWindow } from '@/utils/DynamicUtils'

const route = useRoute()

const { tableData, loading, searchParam, searchMethod: loadSimpleTasks } = useTableAndSearchForm({
  defaultParam: { keyword: '' },
  searchMethod: SimpleTaskApi.search
})

const { userOptions, loadUsersAndRefreshOptions } = useAllUsers(searchParam)

const { initLoadOnce } = useInitLoadOnce(async () => {
  await loadUsersAndRefreshOptions()
  await loadSimpleTasks()
})

onMounted(initLoadOnce)

onActivated(initLoadOnce)

const columns = [{
  labelKey: 'api.label.taskName',
  prop: 'taskName',
  minWidth: '120px'
}, {
  labelKey: 'api.label.projectName',
  prop: 'projectName',
  minWidth: '120px',
  enabled: checkShowColumn(tableData.value, 'projectCode'),
  click (item) {
    if (item.projectCode) {
      $goto(`/api/projects/${item.projectCode}?backUrl=${route.fullPath}`)
    }
  }
}, {
  labelKey: 'common.label.user',
  prop: 'userName'
}, {
  labelKey: 'api.label.runningStatus',
  formatter (data) {
    if (data.taskStatus) {
      const type = TASK_STATUS_MAPPING[data.taskStatus] || 'info'
      return <ElTag type={type}>{data.taskStatus}</ElTag>
    }
  },
  attrs: {
    align: 'center'
  }
}, {
  labelKey: 'api.label.triggerRate',
  formatter (data) {
    if (data.type === 'FixedRate') {
      return dayjs.duration(data.triggerRate, 'milliseconds').humanize()
    }
    return data.cron
  }
}, {
  labelKey: 'api.label.execDate',
  property: 'lastExecDate',
  dateFormat: 'YYYY-MM-DD HH:mm:ss',
  minWidth: '120px'
}, {
  labelKey: 'api.label.lastExecStatus',
  minWidth: '120px',
  formatter (data) {
    const lastLog = data.lastLog
    if (!lastLog) {
      return <ElText type="info">-</ElText>
    }
    const isSuccess = lastLog.logResult === 'SUCCESS'
    const tagType = isSuccess ? 'success' : 'danger'
    const formatCostTime = (ms) => {
      if (ms == null) return ''
      return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`
    }
    const timeStr = formatCostTime(lastLog.logTime)
    const tooltipText = lastLog.logMessage || $i18nBundle('api.msg.clickToViewTaskLogs')

    return (
      <div
        class="flex pointer"
        style="line-height: 1.3; cursor: pointer; display: inline-flex;"
        onClick={() => showTaskLogsWindow(data)}
      >
        <ElTooltip
          content={tooltipText}
          placement="top"
          showAfter={200}
          popper-style="max-width: 450px; word-break: break-all;"
        >
          <div class="flex items-center gap-1">
            <ElTag size="small" type={tagType}>
              {lastLog.logResult}
            </ElTag>
            {timeStr && <ElText size="small" type="info">({timeStr})</ElText>}
          </div>
        </ElTooltip>
      </div>
    )
  }
}]

const buttons = computed(() => {
  return [{
    labelKey: 'api.label.manualImportData1',
    type: 'success',
    click: item => {
      $coreConfirm($i18nKey('common.msg.commonConfirm', 'api.label.manualImportData1'))
        .then(() => triggerSimpleTask(item.taskId, { loading: true, timeout: 60000 })
          .then((data) => {
            if (data.success) {
              $coreAlert(data.message)
              loadSimpleTasks()
            }
          }))
    }
  }, {
    labelKey: 'api.label.stopTask',
    type: 'danger',
    click: item => {
      $coreConfirm($i18nKey('common.msg.commonConfirm', 'api.label.stopTask'))
        .then(() => SimpleTaskApi.deleteById(item.taskId))
        .then(() => loadSimpleTasks())
    }
  }, {
    labelKey: 'api.label.stopAndDisable',
    type: 'danger',
    buttonIf (item) {
      return !!item.tid
    },
    click: item => {
      const { taskId, tid } = item
      $coreConfirm($i18nKey('common.msg.commonConfirm', 'api.label.stopAndDisable'))
        .then(() => removeAndDisable({ taskId, tid }))
        .then(() => loadSimpleTasks())
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
      loadSimpleTasks()
    }
  }, {
    labelKey: 'common.label.keywords',
    prop: 'keyword'
  }
  ]
})

</script>

<template>
  <el-container class="flex-column">
    <common-form
      inline
      :model="searchParam"
      :options="searchFormOptions"
      :submit-label="$t('common.label.search')"
      @submit-form="loadSimpleTasks()"
    />
    <common-table
      frontend-paging
      :data="tableData"
      :buttons-column-attrs="{minWidth:'200px'}"
      :columns="columns"
      :buttons="buttons"
      :loading="loading"
    />
  </el-container>
</template>

<style scoped>

</style>
