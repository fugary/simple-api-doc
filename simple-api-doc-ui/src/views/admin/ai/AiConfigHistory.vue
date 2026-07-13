<script setup lang="jsx">
import { ref, computed } from 'vue'
import { AiConfigApi, recoverAiConfigFromHistory } from '@/api/AiConfigApi'
import { $coreConfirm, formatDate } from '@/utils'
import { ElMessage, ElTag } from 'element-plus'
import { $i18nBundle } from '@/messages'

const visible = ref(false)
const loading = ref(false)
const historyList = ref([])
const currentConfig = ref(null)

const emit = defineEmits(['restored'])

const show = async (row) => {
  currentConfig.value = row
  visible.value = true
  await loadHistory()
}

const loadHistory = async () => {
  if (!currentConfig.value) return
  loading.value = true
  try {
    const res = await AiConfigApi.search({
      modifyFrom: currentConfig.value.id,
      page: { current: 1, size: 100 }
    })
    if (res.success) {
      historyList.value = res.resultData || []
    }
  } finally {
    loading.value = false
  }
}

const recoverHistory = (historyRow) => {
  $coreConfirm(`确定要将配置恢复到 V${historyRow.version} 吗？`)
    .then(async () => {
      const res = await recoverAiConfigFromHistory({
        queryId: historyRow.id
      })
      if (res.success) {
        ElMessage.success($i18nBundle('common.msg.saveSuccess'))
        visible.value = false
        emit('restored')
      } else {
        ElMessage.error(res.message || $i18nBundle('common.msg.saveFailed'))
      }
    })
    .catch(() => {})
}

defineExpose({
  show
})

// Columns
const columns = computed(() => {
  return [{
    labelKey: 'common.label.version',
    label: '版本',
    prop: 'version',
    width: '80',
    align: 'center',
    formatter (data) {
      return <ElTag type="info">V{data.version}</ElTag>
    }
  }, {
    labelKey: 'api.label.configName',
    label: '配置名称',
    minWidth: '150px',
    prop: 'configName'
  }, {
    labelKey: 'api.label.baseUrl',
    label: 'Base URL',
    prop: 'baseUrl',
    minWidth: '200px',
    showOverflowTooltip: true
  }, {
    labelKey: 'api.label.defaultModel',
    label: '默认模型',
    prop: 'defaultModel',
    width: '120px'
  }, {
    labelKey: 'common.label.modifier',
    label: '修改人',
    prop: 'creator',
    width: '100px'
  }, {
    labelKey: 'common.label.modifyDate',
    label: '修改时间',
    prop: 'createDate',
    width: '160px',
    formatter: (row) => formatDate(row.createDate)
  }]
})

const buttons = computed(() => {
  return [{
    labelKey: 'common.label.recover',
    label: '恢复此版本',
    type: 'primary',
    icon: 'RefreshLeft',
    click: recoverHistory
  }]
})
</script>

<template>
  <common-window
    v-model="visible"
    :title="$t('api.label.historyVersions')"
    width="800px"
    :show-footer="false"
  >
    <common-table
      :data="historyList"
      :columns="columns"
      :buttons="buttons"
      :loading="loading"
      :show-pagination="false"
      max-height="400px"
      :buttons-column-attrs="{width:'150px'}"
    />
  </common-window>
</template>

<style scoped>
</style>
