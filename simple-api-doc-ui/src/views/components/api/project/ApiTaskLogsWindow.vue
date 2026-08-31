<script setup lang="jsx">
import { ref, computed } from 'vue'
import { useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useDefaultPage } from '@/config'
import ApiLogApi from '@/api/ApiLogApi'
import { showCodeWindow } from '@/utils/DynamicUtils'
import { ElTag, ElText, ElTooltip } from 'element-plus'
import { $i18nBundle, $i18nKey } from '@/messages'

const showWindow = ref(false)
const currentTask = ref({})

const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: {
    logName: 'ProjectAutoImportInvoker#importProject',
    dataId: '',
    page: useDefaultPage(10)
  },
  saveParam: false,
  searchMethod: ApiLogApi.search
})

const formatCostTime = (ms) => {
  if (ms == null) return '-'
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`
}

const columns = computed(() => {
  return [{
    labelKey: 'api.label.logResult',
    width: '90px',
    formatter (data) {
      if (data.logResult) {
        const type = data.logResult === 'SUCCESS' ? 'success' : 'danger'
        return <ElTag type={type}>{data.logResult}</ElTag>
      }
      return '-'
    }
  }, {
    labelKey: 'api.label.logTime',
    width: '80px',
    formatter (data) {
      return formatCostTime(data.logTime)
    }
  }, {
    labelKey: 'common.label.createDate',
    property: 'createDate',
    dateFormat: 'YYYY-MM-DD HH:mm:ss',
    width: '170px'
  }, {
    labelKey: 'api.label.logMessage',
    minWidth: '320px',
    formatter (data) {
      const isSuccess = data.logResult === 'SUCCESS'
      const msg = data.logMessage || data.exceptions || '-'
      return (
        <ElTooltip
          content={msg}
          placement="top"
          showAfter={200}
          popper-style="max-width: 500px; word-break: break-all;"
        >
          <ElText size="small" type={isSuccess ? '' : 'danger'} truncated style="max-width: 500px; cursor: default;">
            {msg}
          </ElText>
        </ElTooltip>
      )
    }
  }]
})

const showLogDetail = (item) => {
  showCodeWindow(JSON.stringify(item), {
    showSelectButton: true,
    buttons: [{
      enabled: !!item.logData,
      type: 'info',
      label: $i18nKey('common.label.commonView', 'api.label.requestBody1'),
      click: () => {
        showCodeWindow(item.logData)
      }
    }, {
      enabled: !!item.responseBody,
      type: 'info',
      label: $i18nKey('common.label.commonView', 'api.label.responseBody1'),
      click: () => {
        showCodeWindow(item.responseBody)
      }
    }, {
      enabled: !!item.exceptions,
      type: 'danger',
      label: $i18nKey('common.label.commonView', 'api.label.exceptions'),
      click: () => {
        showCodeWindow(item.exceptions)
      }
    }]
  })
}

const buttons = [{
  labelKey: 'common.label.view',
  type: 'primary',
  click: showLogDetail
}]

const showTaskLogs = (task) => {
  currentTask.value = task || {}
  searchParam.value.dataId = String(task.id || task.tid || '')
  searchParam.value.page.current = 1
  searchMethod()
  showWindow.value = true
}

defineExpose({
  showTaskLogs
})
</script>

<template>
  <common-window
    v-model="showWindow"
    width="1000px"
    :show-cancel="false"
    :ok-label="$t('common.label.close')"
    destroy-on-close
    :title="$i18nBundle('api.label.taskExecLogsTitle', [currentTask.taskName || currentTask.projectName || ''])"
    append-to-body
    show-fullscreen
  >
    <el-container class="flex-column">
      <common-table
        v-model:page="searchParam.page"
        :data="tableData"
        :columns="columns"
        :buttons="buttons"
        :buttons-column-attrs="{ minWidth: '90px', fixed: 'right' }"
        :loading="loading"
        @page-size-change="searchMethod()"
        @current-page-change="searchMethod()"
        @row-dblclick="showLogDetail($event)"
      />
    </el-container>
  </common-window>
</template>

<style scoped>
</style>
