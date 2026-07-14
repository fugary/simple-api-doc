<script setup lang="jsx">
import { ref, onMounted, onActivated, computed } from 'vue'
import { formatDate, $coreConfirm } from '@/utils'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import { AiConfigApi, recoverAiConfigFromHistory, loadHistoryDiff, searchHistories } from '@/api/AiConfigApi'
import { ElMessage, ElTag, ElText } from 'element-plus'
import { useDefaultPage } from '@/config'
import { $i18nBundle } from '@/messages'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import { showHistoryListWindow, showApiCompareWindow } from '@/utils/DynamicUtils'
import { defineTableColumns } from '@/components/utils'

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

const historyColumns = defineTableColumns([{
  labelKey: 'common.label.version',
  prop: 'version',
  width: '120',
  align: 'center',
  formatter: (data) => {
    const currentFlag = data.current ? <ElTag type="success" size="small">{$i18nBundle('api.label.current')}</ElTag> : ''
    return <>
      <span class="margin-right2">{data.version}</span>
      {currentFlag}
    </>
  }
}, {
  labelKey: 'api.label.configName',
  prop: 'configName',
  minWidth: '150px'
}, {
  labelKey: 'api.label.baseUrl',
  prop: 'baseUrl',
  minWidth: '200px',
  showOverflowTooltip: true
}, {
  labelKey: 'api.label.defaultModel',
  prop: 'defaultModel',
  width: '150px',
  align: 'center'
}, {
  labelKey: 'common.label.modifier',
  formatter (data) {
    return <ElText>{data.modifier || data.creator}</ElText>
  },
  width: '120px',
  align: 'center'
}, {
  labelKey: 'common.label.modifyDate',
  property: 'createDate',
  width: '170px',
  align: 'center',
  formatter: (data) => formatDate(data.createDate || data.modifyDate)
}])

const historyOptionsMethod = (doc, history) => {
  return [
    { labelKey: 'api.label.configName', prop: 'configName' },
    { labelKey: 'api.label.provider', prop: 'provider' },
    { labelKey: 'api.label.baseUrl', prop: 'baseUrl' },
    { labelKey: 'api.label.apiKey', prop: 'apiKey' },
    { labelKey: 'api.label.defaultModel', prop: 'defaultModel' },
    { labelKey: 'common.label.status', prop: (doc) => doc.status === 1 ? $i18nBundle('common.label.statusEnable') : $i18nBundle('common.label.statusDisable') },
    { labelKey: 'api.label.setDefault', prop: (doc) => doc.isDefault === 1 ? $i18nBundle('common.label.yes') : $i18nBundle('common.label.no') },
    { labelKey: 'common.label.version', prop: () => `${doc.version ?? ''}${doc.current ? ` <${$i18nBundle('api.label.current')}>` : ''}` },
    { labelKey: 'common.label.modifyDate', prop: () => formatDate(doc[history ? 'createDate' : 'modifyDate']) },
    { labelKey: 'common.label.modifier', prop: () => doc[history ? 'creator' : 'modifier'] || doc.creator }
  ]
}

const showHistoryDialog = (row) => {
  showHistoryListWindow({
    title: $i18nBundle('api.label.historyVersions'),
    columns: historyColumns,
    searchFunc: (param, config) => searchHistories(row.id, param, config),
    compareFunc: async (modified, target, previous) => {
      let original = modified
      if (previous) {
        const { resultData } = await loadHistoryDiff({
          queryId: modified.id,
          version: modified.version
        })
        modified = resultData?.modified || {}
        original = resultData?.original || {}
        modified.current = !modified.modifyFrom
      } else {
        modified = target
      }
      showApiCompareWindow({
        original,
        modified,
        historyOptionsMethod
      })
    },
    recoverFunc: (param) => recoverAiConfigFromHistory(param),
    onUpdateHistory: () => searchMethod()
  })
}

// Columns
const columns = computed(() => {
  return [{
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
      const typeMap = {
        OPENAI: 'success',
        ANTHROPIC: 'warning',
        GEMINI: '' // primary
      }
      const type = typeMap[data.provider] !== undefined ? typeMap[data.provider] : 'info'
      return <ElTag type={type}>{data.provider}</ElTag>
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

const PROVIDER_BASE_URL_HINTS = {
  OPENAI: 'https://api.openai.com/v1',
  ANTHROPIC: 'https://api.anthropic.com/v1',
  GEMINI: 'https://generativelanguage.googleapis.com/v1beta'
}

const queryBaseUrlSuggestions = (queryString, cb) => {
  const provider = editForm.value.provider
  const defaultUrl = PROVIDER_BASE_URL_HINTS[provider]
  const suggestions = []
  if (defaultUrl) {
    suggestions.push({ value: defaultUrl })
  }
  // 如果用户输入了内容，也可以保留用户的输入作为建议，或者只显示默认的
  if (queryString && queryString !== defaultUrl) {
    suggestions.push({ value: queryString })
  }
  cb(suggestions)
}

// Edit Form Options
const editFormOptions = computed(() => {
  const isSystem = editForm.value.creator === 'system'
  return [{
    labelKey: 'api.label.configName',
    prop: 'configName',
    required: true,
    disabled: isSystem
  }, {
    labelKey: 'api.label.provider',
    prop: 'provider',
    type: 'select',
    disabled: isSystem,
    children: [
      { label: 'OpenAI (含兼容模型)', value: 'OPENAI' },
      { label: 'Anthropic Claude', value: 'ANTHROPIC' },
      { label: 'Google Gemini', value: 'GEMINI' }
    ]
  }, {
    labelKey: 'api.label.baseUrl',
    prop: 'baseUrl',
    type: 'autocomplete',
    required: true,
    disabled: isSystem,
    attrs: {
      fetchSuggestions: queryBaseUrlSuggestions,
      clearable: true
    }
  }, {
    labelKey: 'api.label.apiKey',
    prop: 'apiKey',
    type: 'input',
    disabled: isSystem,
    attrs: {
      type: 'password',
      showPassword: true
    }
  }, {
    labelKey: 'api.label.defaultModel',
    prop: 'defaultModel',
    disabled: isSystem,
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
      @row-dblclick="showEditDialog($event)"
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
  </el-container>
</template>

<style scoped>
</style>
