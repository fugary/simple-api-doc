<script setup lang="jsx">
import { computed, onActivated, onMounted } from 'vue'
import { $coreConfirm, isAdminUser } from '@/utils'
import { useTableAndSearchForm } from '@/hooks/CommonHooks'
import SimpleTaskApi, { removeAndDisable } from '@/api/SimpleTaskApi'
import { $i18nKey } from '@/messages'
import { TASK_STATUS_MAPPING } from '@/consts/ApiConstants'
import { ElTag } from 'element-plus'
import dayjs from 'dayjs'
import { useAllUsers } from '@/api/ApiUserApi'

const { tableData, loading, searchParam, searchMethod: loadSimpleTasks } = useTableAndSearchForm({
  defaultParam: { keyword: '' },
  searchMethod: SimpleTaskApi.search
})

onMounted(() => {
  loadSimpleTasks()
})

const { userOptions, loadUsersAndRefreshOptions } = useAllUsers(searchParam)

onActivated(async () => {
  await loadUsersAndRefreshOptions()
  loadSimpleTasks()
})

const columns = [{
  labelKey: 'api.label.taskName',
  prop: 'taskName',
  minWidth: '120px'
}, {
  labelKey: 'api.label.projectName',
  prop: 'projectName',
  minWidth: '120px'
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
      :buttons-column-attrs="{minWidth:'150px'}"
      :columns="columns"
      :buttons="buttons"
      :loading="loading"
    />
  </el-container>
</template>

<style scoped>

</style>
