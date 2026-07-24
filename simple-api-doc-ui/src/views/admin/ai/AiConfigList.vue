<script setup lang="jsx">
import { ref, onMounted, onActivated, computed } from 'vue'
import { omit } from 'lodash-es'
import { formatDate, $coreConfirm } from '@/utils'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import { AiConfigApi, recoverAiConfigFromHistory, loadHistoryDiff, searchHistories } from '@/api/AiConfigApi'
import { ElMessage, ElTag, ElText, ElSwitch } from 'element-plus'
import { useDefaultPage } from '@/config'
import { $i18nBundle, $i18nKey } from '@/messages'
import { useSearchStatus, useFormStatus } from '@/consts/GlobalConstants'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import { showHistoryListWindow, showApiCompareWindow } from '@/utils/DynamicUtils'
import { defineTableColumns } from '@/components/utils'
import AiConfigTestWindow from './AiConfigTestWindow.vue'

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

const copyConfig = async (row) => {
  await showEditDialog(row)
  const rest = omit(editForm.value, ['id', 'version', 'creator', 'createDate', 'modifier', 'modifyDate', 'modifyFrom'])
  editForm.value = {
    ...rest,
    configName: `${rest.configName || ''}-copy`,
    isDefault: 0
  }
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

const handleToggleDefault = (row) => {
  const toValue = row.isDefault === 1 ? 0 : 1
  const confirmMsg = $i18nBundle('common.msg.commonConfirm', [$i18nBundle('api.label.setDefault')])
  return $coreConfirm(confirmMsg)
    .then(() => saveConfig({ ...row, isDefault: toValue }))
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
  minWidth: '120px',
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
  minWidth: '150px',
  align: 'center'
}, {
  labelKey: 'api.label.setDefault',
  prop: 'isDefault',
  minWidth: '100px',
  align: 'center',
  formatter (data) {
    return <DelFlagTag v-model={data.isDefault}
                       switchMode={false}
                       typeConfig={{ 1: 'primary', 0: 'info' }}
                       valueConfig={{
                         1: $i18nBundle('common.label.yes'),
                         0: $i18nBundle('common.label.no')
                       }} />
  }
}, {
  labelKey: 'common.label.status',
  prop: 'status',
  minWidth: '100px',
  align: 'center',
  formatter (data) {
    return <DelFlagTag v-model={data.status} switchMode={false} />
  }
}, {
  labelKey: 'common.label.modifier',
  formatter (data) {
    return <ElText>{data.modifier || data.creator}</ElText>
  },
  minWidth: '120px',
  align: 'center'
}, {
  labelKey: 'common.label.modifyDate',
  property: 'createDate',
  minWidth: '170px',
  align: 'center',
  formatter: (data) => formatDate(data.modifyDate || data.createDate)
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
      return <ElText tag="strong">{data.configName}</ElText>
    }
  }, {
    labelKey: 'api.label.provider',
    prop: 'provider',
    minWidth: '120px',
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
    minWidth: '150px',
    align: 'center'
  }, {
    labelKey: 'api.label.baseUrl',
    prop: 'baseUrl',
    minWidth: '200px',
    showOverflowTooltip: true
  }, {
    labelKey: 'api.label.setDefault',
    prop: 'isDefault',
    minWidth: '100px',
    align: 'center',
    formatter (data) {
      const toText = data.isDefault === 1 ? $i18nBundle('common.label.statusDisabled') : $i18nBundle('api.label.setDefault')
      const tooltip = $i18nKey('common.msg.clickTo', toText)
      return <el-link v-common-tooltip={tooltip} underline="never">
        <ElSwitch v-model={data.isDefault}
                  active-value={1}
                  inactive-value={0}
                  before-change={() => handleToggleDefault(data)}
                  onClick={(e) => e.stopPropagation()} />
      </el-link>
    }
  }, {
    labelKey: 'common.label.status',
    prop: 'status',
    minWidth: '100px',
    align: 'center',
    formatter (data) {
      return <DelFlagTag v-model={data.status}
                         clickToToggle={true}
                         onToggleValue={(status) => saveConfig({ ...data, status: Number(status) })} />
    }
  }, {
    labelKey: 'common.label.createDate',
    property: 'createDate',
    minWidth: '170px',
    align: 'center',
    formatter: (row) => formatDate(row.createDate)
  }]
})

const testDialogVisible = ref(false)
const testConfigId = ref(null)

const showTestDialog = (row) => {
  testConfigId.value = row.id
  testDialogVisible.value = true
}

const testDefaultConfig = async () => {
  const res = await AiConfigApi.search({ isDefault: 1, status: 1 })
  if (res.success && res.resultData?.length > 0) {
    showTestDialog(res.resultData[0])
  } else {
    ElMessage.warning($i18nBundle('api.msg.noDefaultConfig'))
  }
}

const buttons = computed(() => {
  return [{
    labelKey: 'common.label.edit',
    type: 'primary',
    click: showEditDialog
  }, {
    labelKey: 'common.label.copy',
    type: 'warning',
    click: copyConfig
  }, {
    labelKey: 'common.label.test',
    type: 'success',
    click: showTestDialog
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
    labelKey: 'api.label.provider',
    prop: 'provider',
    type: 'select',
    children: [
      { label: 'OpenAI', value: 'OPENAI' },
      { label: 'Anthropic Claude', value: 'ANTHROPIC' },
      { label: 'Google Gemini', value: 'GEMINI' }
    ],
    change () {
      searchMethod()
    }
  }, {
    labelKey: 'api.label.defaultModel',
    prop: 'defaultModel'
  },
  useSearchStatus({ change: searchMethod })
  ]
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
  },
  useFormStatus()
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
      @submit-form="searchMethod()"
    >
      <template #buttons>
        <el-button
          type="success"
          @click="testDefaultConfig()"
        >
          {{ $t('common.label.test') }}
        </el-button>
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
      :buttons-column-attrs="{minWidth:'340px', fixed: 'right'}"
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
    <AiConfigTestWindow
      v-if="testDialogVisible"
      v-model="testDialogVisible"
      :config-id="testConfigId"
    />
  </el-container>
</template>

<style scoped>
</style>
