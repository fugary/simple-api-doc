<script setup lang="jsx">
import { ref, onMounted, onActivated, computed } from 'vue'
import { formatDate, $coreConfirm } from '@/utils'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import { AiConfigApi } from '@/api/AiConfigApi'
import { ElMessage, ElTag, ElText } from 'element-plus'
import { useDefaultPage } from '@/config'
import AiConfigHistory from './AiConfigHistory.vue'
import { $i18nBundle } from '@/messages'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'

const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { configName: '', status: '', isDefault: '', page: useDefaultPage() },
  searchMethod: AiConfigApi.search
})

const { initLoadOnce } = useInitLoadOnce(async () => {
  await searchMethod()
})

onMounted(initLoadOnce)
onActivated(initLoadOnce)

const editDialogVisible = ref(false)
const editForm = ref({})
const historyDialogRef = ref(null)

const showEditDialog = async (row) => {
  if (row && row.id) {
    const res = await AiConfigApi.getById(row.id)
    if (res.success) {
      editForm.value = res.resultData
    }
  } else {
    editForm.value = {
      provider: 'OPENAI',
      status: 1,
      isDefault: 0
    }
  }
  editDialogVisible.value = true
}

const saveConfig = async (data) => {
  const res = await AiConfigApi.saveOrUpdate(data)
  if (res.success) {
    searchMethod()
    return res
  } else {
    ElMessage.error(res.message || $i18nBundle('common.msg.saveFailed'))
    throw new Error('Save failed')
  }
}

const deleteConfig = (row) => {
  $coreConfirm($i18nBundle('common.msg.deleteConfirm'))
    .then(async () => {
      const res = await AiConfigApi.deleteById(row.id)
      if (res.success) {
        ElMessage.success($i18nBundle('common.msg.deleteSuccess'))
        searchMethod()
      } else {
        ElMessage.error(res.message || $i18nBundle('common.msg.deleteFailed'))
      }
    })
    .catch(() => {})
}

const showHistoryDialog = (row) => {
  if (historyDialogRef.value) {
    historyDialogRef.value.show(row)
  }
}

// Columns
const columns = computed(() => {
  return [{
    label: 'ID',
    prop: 'id',
    width: '80',
    align: 'center'
  }, {
    labelKey: 'api.label.configName',
    minWidth: '150px',
    prop: 'configName',
    formatter (data) {
      const tag = data.isDefault === 1 ? <ElTag type="success" size="small" style="margin-left: 8px;">{$i18nBundle('api.label.default')}</ElTag> : null
      return <><ElText tag="strong">{data.configName}</ElText>{tag}</>
    }
  }, {
    labelKey: 'api.label.provider',
    prop: 'provider',
    width: '120',
    align: 'center',
    formatter (data) {
      return <ElTag type="info">{data.provider}</ElTag>
    }
  }, {
    labelKey: 'api.label.defaultModel',
    prop: 'defaultModel',
    width: '150',
    align: 'center'
  }, {
    labelKey: 'api.label.baseUrl',
    prop: 'baseUrl',
    minWidth: '200px',
    showOverflowTooltip: true
  }, {
    labelKey: 'common.label.status',
    prop: 'status',
    width: '100',
    align: 'center',
    formatter (data) {
      return <ElTag type={data.status === 1 ? 'success' : 'danger'}>{data.status === 1 ? $i18nBundle('common.label.statusEnable') : $i18nBundle('common.label.statusDisable')}</ElTag>
    }
  }, {
    labelKey: 'common.label.createDate',
    property: 'createDate',
    width: '170',
    align: 'center',
    formatter: (row) => formatDate(row.createDate)
  }]
})

const buttons = computed(() => {
  return [{
    labelKey: 'common.label.edit',
    type: 'primary',
    buttonIf: (row) => row.creator !== 'system',
    click: showEditDialog
  }, {
    labelKey: 'api.label.history',
    type: 'info',
    buttonIf: (row) => row.version > 1,
    click: showHistoryDialog
  }, {
    labelKey: 'common.label.delete',
    type: 'danger',
    buttonIf: (row) => row.creator !== 'system',
    click: deleteConfig
  }]
})

// Search Form Options
const searchFormOptions = computed(() => {
  return [{
    labelKey: 'api.label.configName',
    prop: 'configName'
  }, {
    labelKey: 'common.label.status',
    prop: 'status',
    type: 'select',
    children: [
      { labelKey: 'common.label.statusEnable', value: 1 },
      { labelKey: 'common.label.statusDisable', value: 0 }
    ],
    change () {
      searchMethod()
    }
  }]
})

// Edit Form Options
const editFormOptions = computed(() => {
  return [{
    labelKey: 'api.label.configName',
    prop: 'configName',
    required: true
  }, {
    labelKey: 'api.label.provider',
    prop: 'provider',
    type: 'select',
    children: [
      { label: 'OpenAI (含兼容模型)', value: 'OPENAI' },
      { label: 'Anthropic Claude', value: 'ANTHROPIC' },
      { label: 'Google Gemini', value: 'GEMINI' }
    ]
  }, {
    labelKey: 'api.label.baseUrl',
    prop: 'baseUrl',
    required: true
  }, {
    labelKey: 'api.label.apiKey',
    prop: 'apiKey',
    type: 'input',
    attrs: {
      type: 'password',
      showPassword: true
    }
  }, {
    labelKey: 'api.label.defaultModel',
    prop: 'defaultModel',
    required: true
  }, {
    labelKey: 'api.label.setDefault',
    prop: 'isDefault',
    type: 'switch',
    attrs: {
      activeValue: 1,
      inactiveValue: 0
    }
  }, {
    labelKey: 'common.label.status',
    prop: 'status',
    type: 'switch',
    attrs: {
      activeValue: 1,
      inactiveValue: 0
    }
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
      @submit-form="searchMethod()"
    >
      <template #buttons>
        <el-button
          type="info"
          @click="showEditDialog()"
        >
          {{ $t('common.label.new') }}
        </el-button>
      </template>
    </common-form>

    <common-table
      v-model:page="searchParam.page"
      :data="tableData"
      :buttons-column-attrs="{width:'220px'}"
      :columns="columns"
      :buttons="buttons"
      :loading="loading"
      @page-size-change="searchMethod()"
      @current-page-change="searchMethod()"
    />

    <simple-edit-window
      v-model="editForm"
      v-model:show-edit-window="editDialogVisible"
      :form-options="editFormOptions"
      :name="$t('api.label.aiConfigManagement')"
      :save-current-item="saveConfig"
      width="600px"
      label-width="120px"
    />

    <AiConfigHistory
      ref="historyDialogRef"
      @restored="searchMethod()"
    />
  </el-container>
</template>

<style scoped>
</style>
