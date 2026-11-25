<script setup>
import { computed, ref, watch } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { isNumber } from 'lodash-es'
import { calcNodeLeaf } from '@/services/api/ApiFolderService'
import {
  COPY_LIST_FIELDS,
  DEFAULT_COPY_LIST_CONFIG,
  collectDocsFromTree,
  formatCopyRows
} from '@/services/api/DocCopyListService'
import { calcEnvConfigs, getEnvOptions } from '@/api/SimpleShareApi'
import TreeCheckConfig from '@/views/components/utils/TreeCheckConfig.vue'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import { $i18nBundle } from '@/messages'
import { $coreAlert, $copyText } from '@/utils'

const FORMAT_ITEMS = [
  { value: 'tsv', labelKey: 'api.label.copyFormatTsv' },
  { value: 'csv', labelKey: 'api.label.copyFormatCsv' },
  { value: 'markdown', labelKey: 'api.label.copyFormatMarkdown' },
  { value: 'text', labelKey: 'api.label.copyFormatText' }
]

const FIELD_LABEL_KEYS = {
  docName: 'api.label.copyFieldDocName',
  method: 'api.label.copyFieldMethod',
  url: 'api.label.copyFieldUrl',
  folderPath: 'api.label.copyFieldFolderPath',
  docType: 'api.label.copyFieldDocType',
  status: 'common.label.status'
}

const SEPARATOR_ITEMS = [
  { value: ' ', labelKey: 'api.label.copySepSpace' },
  { value: ' - ', labelKey: 'api.label.copySepDash' },
  { value: ' | ', labelKey: 'api.label.copySepPipe' },
  { value: '\t', labelKey: 'api.label.copySepTab' }
]

const props = defineProps({
  shareDoc: { type: Object, default: undefined },
  projectItem: { type: Object, default: undefined },
  treeNodes: { type: Array, default: () => [] }
})

const showWindow = defineModel({ type: Boolean, default: false })
const treeSelectKeys = defineModel('treeSelectKeys', { type: Array, default: () => [] })

const fullscreen = ref(false)
const baseUrl = ref()

const copyConfig = useLocalStorage('api-doc-copy-list-config', {
  ...DEFAULT_COPY_LIST_CONFIG,
  fields: [...DEFAULT_COPY_LIST_CONFIG.fields]
}, { mergeDefaults: true })

const envEntries = computed(() => {
  try {
    return calcEnvConfigs(props.shareDoc?.envContent || props.projectItem?.envContent)
  } catch {
    return []
  }
})
const envOptions = computed(() => getEnvOptions(envEntries.value))

watch(envEntries, (entries) => {
  if (baseUrl.value && !entries.some(item => item.url === baseUrl.value)) {
    baseUrl.value = undefined
  }
}, { immediate: true })

const toOptions = (items) => items.map(({ value, labelKey }) => ({ value, label: $i18nBundle(labelKey) }))
const formatOptions = computed(() => toOptions(FORMAT_ITEMS))
const fieldOptions = computed(() => COPY_LIST_FIELDS.map(value => ({ value, label: $i18nBundle(FIELD_LABEL_KEYS[value]) })))
const separatorOptions = computed(() => toOptions(SEPARATOR_ITEMS))

const selectedDocIds = computed(() => (treeSelectKeys.value || []).filter(id => isNumber(id)))
const orderedFields = computed(() => {
  const selected = new Set(copyConfig.value.fields || [])
  return COPY_LIST_FIELDS.filter(field => selected.has(field))
})

const fieldLabels = computed(() => Object.fromEntries(fieldOptions.value.map(item => [item.value, item.label])))
const valueLabels = computed(() => ({
  docTypeApi: 'API',
  docTypeMd: 'Markdown',
  statusEnabled: $i18nBundle('common.label.statusEnabled'),
  statusDisabled: $i18nBundle('common.label.statusDisabled'),
  statusDeprecated: $i18nBundle('api.label.deprecated')
}))

const collectedDocs = computed(() => {
  return collectDocsFromTree(props.treeNodes, selectedDocIds.value, {
    onlyApi: copyConfig.value.onlyApi,
    excludeDisabled: copyConfig.value.excludeDisabled,
    excludeDeprecated: copyConfig.value.excludeDeprecated
  })
})

const copyResult = computed(() => {
  return formatCopyRows(collectedDocs.value, {
    format: copyConfig.value.format,
    fields: orderedFields.value,
    includeHeader: copyConfig.value.includeHeader,
    deduplicate: copyConfig.value.deduplicate,
    textSeparator: copyConfig.value.textSeparator,
    baseUrl: baseUrl.value,
    fieldLabels: fieldLabels.value,
    valueLabels: valueLabels.value
  })
})

const okLabel = computed(() => {
  const count = copyResult.value.rowCount
  return selectedDocIds.value.length > 0
    ? $i18nBundle('api.label.copySelectedDocs', [count])
    : $i18nBundle('api.label.copyAllDocs', [count])
})

const copyGeneratedList = () => {
  if (!copyResult.value.text) {
    $coreAlert($i18nBundle('api.msg.copyDocListEmpty'))
    return false
  }
  $copyText(copyResult.value.text)
  return false
}
</script>

<template>
  <common-window
    v-model="showWindow"
    v-model:fullscreen="fullscreen"
    :title="$t('api.label.copyDocList')"
    width="1120px"
    :height="fullscreen ? '100%' : undefined"
    append-to-body
    show-fullscreen
    :show-ok="copyResult.rowCount > 0"
    :ok-label="okLabel"
    :ok-click="copyGeneratedList"
    :cancel-label="$t('common.label.close')"
  >
    <div
      class="copy-doc-layout"
      :class="{ 'is-fullscreen': fullscreen }"
    >
      <div class="copy-doc-tree-pane">
        <tree-check-config
          v-model:selected-keys="treeSelectKeys"
          :tree-nodes="treeNodes"
          node-key="treeId"
          tree-height="100%"
          style="max-height: 100%;"
        >
          <template #default="{ node, data }">
            <el-text :type="!data.enabled ? 'danger' : (data.deprecated ? 'warning' : '')">
              <tree-icon-label
                :show-icon="shareDoc?.showTreeIcon !== false"
                :node="node"
                :icon-leaf="calcNodeLeaf(data)"
                :url="data.isDoc ? data.url : ''"
              >
                <api-method-tag
                  v-if="data.docType === 'api'"
                  :method="data.method"
                />
                <del v-if="data.deprecated">{{ data.docName || node.label }}</del>
                <span v-else>{{ data.docName || node.label }}</span>
              </tree-icon-label>
            </el-text>
          </template>
        </tree-check-config>
      </div>

      <div class="copy-doc-config-pane">
        <div class="copy-section">
          <div class="copy-section-header">
            <span class="copy-section-label">
              {{ $t('api.label.copyDocListFormat') }}
              <common-icon
                v-common-tooltip="$t('api.msg.copyFormatTsvTip')"
                icon="InfoFilled"
                :size="14"
                class="copy-info-icon"
              />
            </span>
          </div>
          <el-segmented
            v-model="copyConfig.format"
            :options="formatOptions"
            block
          />
        </div>

        <div class="copy-config-card">
          <div class="copy-section">
            <span class="copy-section-label">{{ $t('api.label.copyDocListFields') }}</span>
            <el-checkbox-group
              v-model="copyConfig.fields"
              class="copy-checkbox-group"
            >
              <el-checkbox
                v-for="item in fieldOptions"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </el-checkbox>
            </el-checkbox-group>
          </div>

          <div
            v-if="copyConfig.format === 'text' || (orderedFields.includes('url') && envOptions.length)"
            class="copy-select-grid"
          >
            <div
              v-if="copyConfig.format === 'text'"
              class="copy-select-field"
            >
              <span class="copy-field-label">{{ $t('api.label.copyTextSeparator') }}</span>
              <el-select v-model="copyConfig.textSeparator">
                <el-option
                  v-for="sep in separatorOptions"
                  :key="sep.value"
                  :label="sep.label"
                  :value="sep.value"
                />
              </el-select>
            </div>
            <div
              v-if="orderedFields.includes('url') && envOptions.length"
              class="copy-select-field"
            >
              <span class="copy-field-label">{{ $t('api.label.copyBaseUrlEnv') }}</span>
              <el-select
                v-model="baseUrl"
                clearable
                :placeholder="$t('api.label.copyNoBaseUrl')"
              >
                <el-option
                  v-for="env in envOptions"
                  :key="env.value"
                  :label="`${env.label} - ${env.value}`"
                  :value="env.value"
                />
              </el-select>
            </div>
          </div>

          <div class="copy-checkbox-group copy-options-divider">
            <el-checkbox
              v-model="copyConfig.includeHeader"
              :disabled="copyConfig.format === 'text'"
            >
              {{ $t('api.label.copyIncludeHeader') }}
            </el-checkbox>
            <el-checkbox v-model="copyConfig.onlyApi">
              {{ $t('common.label.onlyApi') }}
            </el-checkbox>
            <el-checkbox v-model="copyConfig.excludeDisabled">
              {{ $t('api.label.copyExcludeDisabled') }}
            </el-checkbox>
            <el-checkbox v-model="copyConfig.excludeDeprecated">
              {{ $t('api.label.copyExcludeDeprecated') }}
            </el-checkbox>
            <el-checkbox v-model="copyConfig.deduplicate">
              {{ $t('api.label.copyDeduplicate') }}
            </el-checkbox>
          </div>
        </div>

        <div class="copy-preview-section">
          <div class="copy-section-header">
            <span class="copy-section-label">{{ $t('common.label.preview') }}</span>
            <el-text
              size="small"
              type="info"
            >
              {{ $t('api.label.copyPreviewCount', [copyResult.rowCount]) }}
            </el-text>
          </div>
          <el-input
            :model-value="copyResult.text"
            type="textarea"
            wrap="off"
            readonly
            resize="none"
            class="copy-preview-textarea"
            :placeholder="$t('api.msg.copyDocListEmpty')"
          />
        </div>
      </div>
    </div>
  </common-window>
</template>

<style scoped>
.copy-doc-layout {
  --copy-pane-height: clamp(450px, calc(85dvh - 160px), 500px);
  display: grid;
  grid-auto-rows: var(--copy-pane-height);
  grid-template-columns: minmax(340px, 1fr) minmax(380px, 1.05fr);
  gap: 18px;
  width: 100%;
  min-width: 0;
}

.copy-doc-layout.is-fullscreen {
  height: 100%;
  --copy-pane-height: minmax(0, 1fr);
}

.copy-doc-tree-pane,
.copy-doc-config-pane {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.copy-doc-config-pane {
  gap: 12px;
  overflow: auto;
}

.copy-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.copy-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.copy-section-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.copy-info-icon {
  color: var(--el-text-color-secondary);
  cursor: help;
}

.copy-config-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 6px;
  background-color: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
}

.copy-checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
}

.copy-checkbox-group :deep(.el-checkbox) {
  margin-right: 0;
  height: 26px;
}

.copy-options-divider {
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.copy-select-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 10px 12px;
}

.copy-select-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.copy-field-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.copy-preview-section {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 110px;
  gap: 6px;
}

.copy-preview-textarea {
  flex: 1;
  min-height: 0;
  display: flex;
}

.copy-preview-textarea :deep(.el-textarea__inner) {
  height: 100%;
  min-height: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre;
  overflow: auto;
}

@media (max-width: 900px) {
  .copy-doc-layout {
    grid-template-columns: 1fr;
  }
}
</style>
