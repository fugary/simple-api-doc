<script setup lang="jsx">
import { computed, ref, watch } from 'vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import { calcNodeLeaf } from '@/services/api/ApiFolderService'
import { ElTag } from 'element-plus'
import { $i18nBundle } from '@/messages'
import { showApiCompareWindow } from '@/utils/DynamicUtils'
import { defineFormOptions, getFrontendPage } from '@/components/utils'

const props = defineProps({ preview: { type: Object, required: true }, importAction: { type: Function, required: true } })
const emit = defineEmits(['confirmed'])
const showWindow = ref(true)
const submitting = ref(false)
const filter = ref({ keyword: '' })
const changedStatuses = ['added', 'updated']
const selectedStatuses = ref(props.preview.changed ? [...changedStatuses] : [])
const allStatuses = computed(() => !selectedStatuses.value.length)
const formOptions = defineFormOptions([
  { prop: 'keyword', labelKey: 'common.label.keywords', attrs: { clearable: true } }
])
const statuses = ['added', 'updated', 'unchanged', 'locked']
const tagTypes = { added: 'success', updated: 'primary', unchanged: 'info', locked: 'info' }
const items = computed(() => props.preview.changes)
const structureHint = computed(() => {
  if (!items.value.some(item => item.type === 'source' && item.status !== 'added')) return ''
  const docs = items.value.filter(item => ['api', 'md'].includes(item.type))
  const added = docs.filter(item => item.status === 'added').length
  if (!docs.length || added / docs.length < 0.8) return ''
  const folders = items.value.filter(item => item.type === 'folder')
  const addedFolders = folders.filter(item => item.status === 'added').length
  // 少量文档也可能导错来源，结合目录新增占比补充判断。
  return docs.length >= 5 || (folders.length && addedFolders / folders.length >= 0.8)
    ? $i18nBundle('api.msg.importStructureHint', [docs.length, added, folders.length, addedFolders])
    : ''
})
const isChanged = item => changedStatuses.includes(item.status)
const categoryOf = item => {
  if (['api', 'md'].includes(item.type)) return 'docs'
  if (item.type === 'folder') return 'folders'
  if (item.type === 'model' && item.path === 'component') return 'models'
  return 'settings'
}
const tabs = computed(() => ['docs', 'models', 'folders', 'settings'].map(name => ({
  name,
  items: items.value.filter(item => categoryOf(item) === name),
  changed: items.value.filter(item => categoryOf(item) === name && isChanged(item)).length
})))
const previewSummary = computed(() => $i18nBundle('api.msg.importPreviewSummary', tabs.value.map(tab => tab.changed)))
const activeTab = ref(tabs.value.find(tab => tab.changed)?.name || 'docs')
const activeItems = computed(() => tabs.value.find(tab => tab.name === activeTab.value)?.items || [])
const schemaTypes = { component: 'api.label.importSchemaComponent', security: 'api.label.importSchemaSecurity', security_requirements: 'api.label.importSchemaSecurityRequirements' }
const typeLabel = item => $i18nBundle(schemaTypes[item.path] && item.type === 'model' ? schemaTypes[item.path] : 'api.label.importType_' + item.type)
const itemName = item => item.type === 'sourceContent'
  ? $i18nBundle('api.label.importType_sourceContent')
  : item.name === item.path && schemaTypes[item.path] ? $i18nBundle(schemaTypes[item.path]) : item.name
const fieldLabels = {
  docContent: 'api.label.docContent',
  schemaContent: 'api.label.importFieldSchema',
  parametersSchema: 'api.label.importFieldParameters',
  requestsSchemas: 'api.label.requestBody',
  responsesSchemas: 'api.label.responseBody',
  securityRequirements: 'api.label.importSchemaSecurityRequirements',
  authType: 'api.label.importFieldAuth',
  authContent: 'api.label.importFieldAuth',
  url: 'api.label.importFieldUrl',
  envContent: 'api.label.importFieldEnvironments',
  description: 'api.label.importFieldDescription',
  summary: 'api.label.importFieldSummary',
  examples: 'api.label.importFieldExamples',
  docName: 'common.label.name',
  projectName: 'common.label.name',
  folderName: 'common.label.name',
  method: 'api.label.importFieldMethod',
  status: 'api.label.importFieldStatus',
  enabled: 'api.label.importFieldStatus',
  deprecated: 'api.label.importFieldDeprecated',
  locked: 'api.label.importFieldLocked',
  sortId: 'api.label.importFieldSort',
  version: 'api.label.importSourceVersion',
  specVersion: 'api.label.importFieldSpecification',
  oasVersion: 'api.label.importFieldSpecification',
  fileName: 'api.label.importSource',
  importType: 'api.label.importSource',
  sourceType: 'api.label.importSource'
}
// 仅标注旧 Markdown 配置补齐默认值；真实规范升级仍按普通变更展示。
const defaultSpecificationUpdates = computed(() => new Set(items.value.filter(item => {
  if (item.type !== 'source' || item.status !== 'updated' ||
    !item.changedFields?.some(field => ['specVersion', 'oasVersion'].includes(field))) return false
  const original = JSON.parse(item.original || '{}')
  const modified = JSON.parse(item.modified || '{}')
  return original?.sourceType === 'markdown' && modified?.sourceType === 'markdown' &&
    ['', 'markdown', 'V31'].includes(original.specVersion || '') &&
    ['', '3.1.0'].includes(original.oasVersion || '') &&
    modified.specVersion === 'V31' && modified.oasVersion === '3.1.0'
})))
const changeSummary = item => {
  if (item.type === 'sourceContent') return $i18nBundle('api.msg.importSourceSnapshot')
  if (!isChanged(item) || item.status === 'added') return $i18nBundle('api.label.importStatus_' + item.status)
  if (item.formatOnly) return $i18nBundle('api.label.importFormatOnly')
  const labels = (item.changedFields || []).map(field => $i18nBundle(
    defaultSpecificationUpdates.value.has(item) && ['specVersion', 'oasVersion'].includes(field)
      ? 'api.label.importDefaultSpecification'
      : fieldLabels[field] || 'api.label.importFieldOther'))
  return [...new Set(labels)].join(' / ') || $i18nBundle('api.label.importFieldOther')
}
const keywordItems = computed(() => {
  const keyword = filter.value.keyword.trim().toLowerCase()
  return activeItems.value.filter(item => !keyword ||
    [itemName(item), item.path, item.method].some(value => value?.toLowerCase().includes(keyword)))
})
const counts = computed(() => Object.fromEntries(statuses.map(status => [status, keywordItems.value.filter(item => item.status === status).length])))
const filtered = computed(() => keywordItems.value.filter(item => allStatuses.value || selectedStatuses.value.includes(item.status)))
const page = ref(getFrontendPage(0, 10))
watch([filtered, () => page.value.pageSize], () => {
  page.value = getFrontendPage(filtered.value.length, page.value.pageSize)
}, { immediate: true })
const pageItems = computed(() => filtered.value.slice((page.value.pageNumber - 1) * page.value.pageSize, page.value.pageNumber * page.value.pageSize))
const toggleStatus = status => {
  const selected = selectedStatuses.value.includes(status)
    ? selectedStatuses.value.filter(value => value !== status)
    : [...selectedStatuses.value, status]
  selectedStatuses.value = selected.length === statuses.length ? [] : selected
}
const clearFilters = () => {
  filter.value.keyword = ''
  selectedStatuses.value = []
}
const columns = computed(() => {
  const tab = activeTab.value
  const result = [
    { labelKey: 'api.label.importChange', width: '100px', formatter: item => <ElTag type={tagTypes[item.status]}>{$i18nBundle('api.label.importStatus_' + item.status)}</ElTag> }
  ]
  result.push({
    labelKey: tab === 'models' ? 'api.label.modelName' : tab === 'settings' ? 'api.label.importSettingItem' : 'common.label.name',
    formatter: item => {
      if (tab === 'docs') {
        return <TreeIconLabel
          class="preview-doc-name"
          node={{ isLeaf: true, label: itemName(item) }}
          iconLeaf={calcNodeLeaf({ isDoc: true, docType: item.type })}
        >
          {item.type === 'api' && item.method && <ApiMethodTag method={item.method} />}
          <span>{itemName(item)}</span>
        </TreeIconLabel>
      }
      return tab === 'settings' && itemName(item) !== typeLabel(item) ? typeLabel(item) + ' · ' + itemName(item) : itemName(item)
    },
    minWidth: '200px',
    attrs: { showOverflowTooltip: true }
  })
  if (['docs', 'folders'].includes(tab)) {
    result.push({ labelKey: tab === 'folders' ? 'api.label.importFolderPath' : 'api.label.importItemPath', prop: 'path', minWidth: '220px', attrs: { showOverflowTooltip: true } })
  }
  result.push({ labelKey: 'api.label.importChangedFields', formatter: changeSummary, minWidth: '220px', attrs: { showOverflowTooltip: true } })
  return result
})
const hasDiff = item => isChanged(item) && item.type !== 'sourceContent'
const showDiff = (item) => {
  if (!hasDiff(item)) return
  showApiCompareWindow({
    title: itemName(item),
    description: defaultSpecificationUpdates.value.has(item) ? $i18nBundle('api.msg.importDefaultSpecification') : '',
    original: { content: item.original || '' },
    modified: { content: item.modified || '' },
    historyOptionsMethod: () => [{ labelKey: 'api.label.importDiffContent', prop: 'content' }]
  })
}
const buttons = [{ labelKey: 'api.label.viewDiff', type: 'primary', buttonIf: hasDiff, click: showDiff }]
const confirm = () => {
  if (submitting.value) return false
  submitting.value = true
  props.importAction().then(result => {
    if (result.success) {
      emit('confirmed', result)
      showWindow.value = false
    }
  }).finally(() => { submitting.value = false })
  return false
}
const allowClose = () => !submitting.value
</script>

<template>
  <common-window
    v-model="showWindow"
    :title="$t('api.label.previewImport')"
    width="min(1100px, 96vw)"
    default-cls="flex-column"
    append-to-body
    show-fullscreen
    :close-on-click-modal="false"
    :show-close="!submitting"
    :show-cancel="!submitting"
    :show-ok="preview.changed"
    :ok-loading="submitting"
    :ok-click="confirm"
    :cancel-click="allowClose"
    :close-click="allowClose"
    :ok-label="$t('api.label.confirmImport')"
  >
    <el-descriptions
      :column="2"
      border
      class="margin-bottom2"
    >
      <el-descriptions-item :label="$t('api.label.importTargetProject')">
        {{ preview.projectName }}
      </el-descriptions-item>
      <el-descriptions-item :label="$t('api.label.importTargetFolder')">
        {{ preview.targetFolder }}
      </el-descriptions-item>
      <el-descriptions-item
        :label="$t('api.label.importSource')"
        :span="2"
      >
        <span class="source-text">{{ preview.source }}</span>
      </el-descriptions-item>
      <el-descriptions-item :label="$t('api.label.importSourceTitle')">
        {{ preview.sourceTitle || '—' }}
      </el-descriptions-item>
      <el-descriptions-item :label="$t('api.label.importSourceVersion')">
        {{ preview.sourceVersion || '—' }}
      </el-descriptions-item>
    </el-descriptions>
    <el-alert
      v-if="structureHint"
      :title="structureHint"
      :description="$t('api.msg.importStructureAdvice')"
      type="warning"
      :closable="false"
      show-icon
      class="margin-bottom2"
    />
    <el-alert
      :title="preview.changed ? previewSummary : $t('api.msg.importPreviewUnchanged')"
      :description="preview.changed ? $t('api.msg.importPreviewHint') : ''"
      :type="preview.changed ? 'primary' : 'success'"
      :closable="false"
      show-icon
      class="margin-bottom2"
    />
    <el-tabs v-model="activeTab">
      <el-tab-pane
        v-for="tab in tabs"
        :key="tab.name"
        :name="tab.name"
        :label="$i18nBundle('api.label.importTabTitle', [$t('api.label.importTab_' + tab.name), tab.changed])"
      />
    </el-tabs>
    <div
      class="check-filter-bar margin-bottom2"
      role="group"
      :aria-label="$t('api.label.importChange')"
    >
      <el-check-tag
        :checked="allStatuses"
        class="check-filter-tag"
        @change="selectedStatuses = []"
      >
        <common-icon
          v-if="allStatuses"
          icon="Check"
          :size="12"
        />
        {{ $t('common.label.all') }} ({{ keywordItems.length }})
      </el-check-tag>
      <el-check-tag
        v-for="status in statuses"
        :key="status"
        :checked="selectedStatuses.includes(status)"
        :type="tagTypes[status]"
        class="check-filter-tag"
        :style="{ '--filter-color': 'var(--el-color-' + tagTypes[status] + ')' }"
        @change="toggleStatus(status)"
      >
        <common-icon
          v-if="selectedStatuses.includes(status)"
          icon="Check"
          :size="12"
        />
        <span
          v-else
          class="check-filter-dot"
        />
        {{ $t('api.label.importStatus_' + status) }} ({{ counts[status] }})
      </el-check-tag>
    </div>
    <common-form
      v-model:model="filter"
      :options="formOptions"
      inline
      :show-buttons="false"
    />
    <common-table
      v-model:page="page"
      :data="pageItems"
      :columns="columns"
      :buttons="buttons"
      :buttons-column-attrs="{ width: '110px' }"
      @row-dblclick="showDiff"
    >
      <template #empty>
        <span>{{ $t(activeItems.length ? 'api.msg.importFilterEmpty' : 'common.msg.noData') }}</span>
        <el-button
          v-if="activeItems.length && (filter.keyword || !allStatuses)"
          link
          type="primary"
          class="margin-left2"
          @click="clearFilters"
        >
          {{ $t('api.label.importViewAll') }}
        </el-button>
      </template>
    </common-table>
  </common-window>
</template>

<style scoped>
.preview-doc-name { width: 100%; min-width: 0; }
.source-text { overflow-wrap: anywhere; }
</style>
