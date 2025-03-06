<script setup lang="jsx">
import { computed, onActivated, onMounted } from 'vue'
import { $coreAlert, $coreConfirm, $goto, isAdminUser } from '@/utils'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import SimpleTaskApi, { removeAndDisable } from '@/api/SimpleTaskApi'
import { $i18nBundle, $i18nKey } from '@/messages'
import { TASK_STATUS_MAPPING } from '@/consts/ApiConstants'
import { ElTag } from 'element-plus'
import dayjs from 'dayjs'
import { useAllUsers } from '@/api/ApiUserApi'
import { useRoute } from 'vue-router'
import { triggerTask } from '@/api/ApiProjectTaskApi'

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
}]

const buttons = computed(() => {
  return [{
    labelKey: 'api.label.importNow',
    type: 'success',
    click: item => {
      $coreConfirm($i18nKey('common.msg.commonConfirm', 'api.label.importNow'))
        .then(() => triggerTask(item.tid, { loading: true, timeout: 60000 })
          .then((data) => {
            if (data.success) {
              $coreAlert($i18nBundle('api.msg.importFileSuccess', [data.resultData?.projectName]))
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
