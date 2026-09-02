<script setup>
import { computed, ref, markRaw } from 'vue'
import { cloneDeep } from 'lodash-es'
import ApiProjectApi, { loadDetailById } from '@/api/ApiProjectApi'
import CommonParamsEdit from '@/views/components/utils/CommonParamsEdit.vue'
import { ElAlert, ElFormItem, ElMessage, ElTabPane, ElTabs } from 'element-plus'
import { $i18nBundle } from '@/messages'
import { DEFAULT_HEADERS } from '@/consts/ApiConstants'
import { defineFormOptions } from '@/components/utils'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import CommonIcon from '@/components/common-icon/index.vue'
import { calcProjectItem } from '@/services/api/ApiProjectService'
import TreeConfigWindow from '@/views/components/utils/TreeConfigWindow.vue'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import { calcNodeLeaf } from '@/services/api/ApiFolderService'
import { useSortableParams } from '@/hooks/CommonHooks'

const extractRulesOptions = defineFormOptions([{
  labelKey: 'common.label.statusEnabled',
  prop: 'enabled',
  type: 'switch',
  value: true,
  width: 60,
  columnAttrs: {
    align: 'center'
  }
}, {
  labelKey: 'api.label.isRegex',
  prop: 'isRegex',
  type: 'switch',
  width: 90,
  columnAttrs: {
    align: 'center'
  }
}, {
  labelKey: 'api.label.matchPath',
  prop: 'apiPath',
  tooltipKey: 'api.msg.matchPathTip',
  minWidth: 320,
  slot: 'matchPathSlot'
}, {
  labelKey: 'api.label.extractPath',
  prop: 'jsonPath',
  required: true,
  tooltipKey: 'api.msg.extractPathTip',
  minWidth: 200,
  headerSlot: 'jsonPathHeader'
}])

const showWindow = ref(false)
const projectItem = ref()
const isLocalMode = ref(false)
const preferenceIdRef = ref('')
const shareConfigStore = useShareConfigStore()
const activeTab = ref('envParams')
const docTreeNodes = ref([])
const showTreeConfigWindow = ref(false)
const treeConfigContext = ref('')
const selectedTreeKeys = ref([])
const currentExtractRule = ref(null)

const isSavedLocal = computed(() => {
  return isLocalMode.value && !!(preferenceIdRef.value && shareConfigStore.getLocalEnvParams(preferenceIdRef.value)?.length)
})

const groupConfig = ref({
  envParams: [],
  loginApiConfigs: []
})

const isTreeNodeDisabled = (nodeData) => {
  return !nodeData.isDoc || nodeData.docType !== 'api'
}

const findDocNodeInTree = (nodes, id) => {
  if (!nodes || !nodes.length) return null
  for (const node of nodes) {
    if (node.isDoc && String(node.id) === String(id)) {
      return node
    }
    if (node.children && node.children.length) {
      const found = findDocNodeInTree(node.children, id)
      if (found) return found
    }
  }
  return null
}

const buildLoginApiConfig = (node) => ({
  apiId: node.id,
  url: node.url,
  method: node.method,
  summary: node.docName || node.label || node.url,
  deprecated: Boolean(node.deprecated),
  enabled: node.enabled !== false && node.status !== 0 && node.deleted !== true
})

const enrichLoginApiConfigs = (configs) => {
  configs?.forEach(c => {
    if (c.apiId) {
      const found = findDocNodeInTree(docTreeNodes.value, c.apiId)
      if (found) Object.assign(c, buildLoginApiConfig(found))
    }
  })
}

const applyApiToExtractRule = (rule, node) => {
  if (!rule || !node) return
  rule.matchType = 'api'
  rule.apiId = node.id
  rule.apiPath = node.url
  rule.apiMethod = node.method
  rule.apiSummary = node.docName || node.label || node.url
  rule.deprecated = Boolean(node.deprecated)
  rule.enabled = node.enabled !== false && node.status !== 0 && node.deleted !== true
}

const enrichExtractRules = (envParams) => {
  envParams?.forEach(param => {
    param.extractRules?.forEach(rule => {
      if (rule.apiId) {
        const found = findDocNodeInTree(docTreeNodes.value, rule.apiId)
        if (found) {
          applyApiToExtractRule(rule, found)
        } else if (rule.matchType === 'api') {
          rule.apiSummary = rule.apiSummary || $i18nBundle('api.label.invalidApi')
          rule.enabled = false
        }
      } else if (!rule.matchType) {
        rule.matchType = 'path'
      }
    })
  })
}

const openExtractApiTreeSelect = (rule) => {
  currentExtractRule.value = rule
  treeConfigContext.value = 'extract'
  selectedTreeKeys.value = rule.apiId ? [rule.apiId] : []
  showTreeConfigWindow.value = true
}

const handleTreeSelectSubmit = (keys) => {
  if (treeConfigContext.value === 'extract') {
    if (currentExtractRule.value && keys?.length) {
      const selectedKey = keys[keys.length - 1]
      const node = findDocNodeInTree(docTreeNodes.value, selectedKey)
      if (node) {
        applyApiToExtractRule(currentExtractRule.value, node)
      }
    }
  } else {
    const currentConfigs = groupConfig.value.loginApiConfigs || []
    const keySet = new Set((keys || []).map(String))
    const updatedConfigs = []

    currentConfigs.forEach(c => {
      if (c.apiId && keySet.has(String(c.apiId))) {
        updatedConfigs.push(c)
        keySet.delete(String(c.apiId))
      }
    })

    keySet.forEach(key => {
      const node = findDocNodeInTree(docTreeNodes.value, key)
      if (node && node.isDoc && node.docType === 'api') {
        updatedConfigs.push(buildLoginApiConfig(node))
      }
    })
    groupConfig.value.loginApiConfigs = updatedConfigs
  }
  showTreeConfigWindow.value = false
}

const switchToCustomPath = (rule) => {
  rule.matchType = 'path'
  rule.apiPath = ''
  delete rule.apiId
  delete rule.apiMethod
  delete rule.apiSummary
}

const parseGroupConfig = (rawConfigStr) => {
  const dbConfig = rawConfigStr ? JSON.parse(rawConfigStr) : { envParams: [], loginApiConfigs: [] }
  const dbLoginApiConfigs = dbConfig.loginApiConfigs || (dbConfig.loginApiConfig ? [dbConfig.loginApiConfig] : [])
  return { dbConfig, dbLoginApiConfigs }
}

let callback
const toEditGroupEnvParams = (projectId, options = {}) => {
  isLocalMode.value = !!options.isLocal
  preferenceIdRef.value = options.preferenceId || projectId
  activeTab.value = 'envParams'

  const pId = projectId || options.preferenceId
  const isNumeric = pId && !isNaN(Number(pId))
  const fetchPromise = isNumeric
    ? loadDetailById(pId, { loading: true, showErrorMessage: false }).catch(() => null)
    : Promise.resolve(null)

  fetchPromise.then(data => {
    projectItem.value = data || {}
    if (data) {
      const calcRes = calcProjectItem(cloneDeep(data))
      docTreeNodes.value = calcRes?.docTreeNodes ? markRaw(calcRes.docTreeNodes) : []
    } else {
      docTreeNodes.value = []
    }
    const { dbConfig, dbLoginApiConfigs } = parseGroupConfig(projectItem.value?.groupConfig)
    if (isLocalMode.value) {
      const localParams = shareConfigStore.getLocalEnvParams(preferenceIdRef.value)
      groupConfig.value = {
        ...dbConfig,
        envParams: (localParams && localParams.length > 0) ? cloneDeep(localParams) : (dbConfig.envParams || []),
        loginApiConfigs: cloneDeep(dbLoginApiConfigs)
      }
    } else {
      groupConfig.value = dbConfig
      groupConfig.value.loginApiConfigs = cloneDeep(dbLoginApiConfigs)
    }
    prepareTreeSearchText(docTreeNodes.value)
    enrichLoginApiConfigs(groupConfig.value.loginApiConfigs)
    enrichExtractRules(groupConfig.value.envParams)
    showWindow.value = true
  })
  return new Promise(resolve => (callback = resolve))
}

const loginApiConfigsParams = computed({
  get: () => groupConfig.value.loginApiConfigs || [],
  set: (val) => (groupConfig.value.loginApiConfigs = val)
})

const {
  sortableRef: loginSortableRef,
  hoverIndex: hoverLoginIndex,
  dragging: loginDragging
} = useSortableParams(loginApiConfigsParams, '.login-api-item')

const openLoginApiTreeSelect = () => {
  treeConfigContext.value = 'login'
  selectedTreeKeys.value = (groupConfig.value.loginApiConfigs || [])
    .map(c => c.apiId)
    .filter(Boolean)
  showTreeConfigWindow.value = true
}

const removeLoginApi = (index) => {
  groupConfig.value.loginApiConfigs.splice(index, 1)
}

const prepareTreeSearchText = (nodes) => {
  if (!nodes || !nodes.length) return
  nodes.forEach(node => {
    const parts = [
      node.docName,
      node.url,
      node.method,
      node.folderName,
      node.label
    ].filter(Boolean)
    node._searchText = parts.join(' ').toLowerCase()
    if (node.children && node.children.length) {
      prepareTreeSearchText(node.children)
    }
  })
}

defineExpose({
  toEditGroupEnvParams
})

const windowButtons = computed(() => {
  if (isSavedLocal.value) {
    return [{
      labelKey: 'api.label.resetDefault',
      type: 'warning',
      click: resetLocal
    }]
  }
  return []
})

const resetLocal = () => {
  if (preferenceIdRef.value) {
    shareConfigStore.resetLocalEnvParams(preferenceIdRef.value)
    const { dbConfig, dbLoginApiConfigs } = parseGroupConfig(projectItem.value?.groupConfig)
    groupConfig.value = cloneDeep(dbConfig)
    groupConfig.value.loginApiConfigs = cloneDeep(dbLoginApiConfigs)
    enrichLoginApiConfigs(groupConfig.value.loginApiConfigs)
    enrichExtractRules(groupConfig.value.envParams)
    ElMessage.success($i18nBundle('common.msg.operationSuccess'))
  }
}

const saveGroupConfig = ({ form }) => {
  form.validate((valid, fields) => {
    if (valid) {
      const configToSave = cloneDeep(groupConfig.value)
      configToSave.envParams?.forEach(param => {
        delete param.showExtractRules
      })
      configToSave.loginApiConfigs = (configToSave.loginApiConfigs || []).filter(c => !!c.apiId)
      if (isLocalMode.value) {
        shareConfigStore.saveLocalEnvParams(preferenceIdRef.value, configToSave.envParams)
        ElMessage.success($i18nBundle('common.msg.saveSuccess'))
        callback?.(configToSave.envParams)
        showWindow.value = false
      } else {
        projectItem.value.groupConfig = JSON.stringify(configToSave)
        ApiProjectApi.saveOrUpdate(projectItem.value)
          .then(() => {
            ElMessage.success($i18nBundle('common.msg.saveSuccess'))
            callback?.(projectItem.value)
            showWindow.value = false
          })
          .catch(err => {
            ElMessage.error(err?.message || $i18nBundle('common.msg.operationFailed'))
          })
      }
    } else {
      console.warn('Form validation failed:', fields)
    }
  })
  return false
}

</script>

<template>
  <common-window
    v-model="showWindow"
    :ok-label="$t('common.label.save')"
    destroy-on-close
    :ok-click="saveGroupConfig"
    :buttons="windowButtons"
    width="1000px"
  >
    <template #header>
      <div style="display: flex; align-items: center; gap: 8px;">
        <span class="el-dialog__title">
          {{ $t('api.label.envAndExtractConfig') }}
          <span
            v-if="isLocalMode"
            style="font-size: 13px; font-weight: normal; color: var(--el-color-primary);"
          >({{ $t('api.msg.saveLocalOnlyTip') }})</span>
        </span>
      </div>
    </template>
    <common-form
      :model="groupConfig"
      :show-buttons="false"
      class="form-edit-width-100"
    >
      <el-tabs
        v-model="activeTab"
        class="common-tabs margin-bottom2"
      >
        <el-tab-pane
          :label="$t('api.label.variableConfig')"
          name="envParams"
        >
          <div class="margin-top2 margin-bottom2">
            <common-params-edit
              v-model="groupConfig.envParams"
              form-prop="envParams"
              name-required
              :name-suggestions="DEFAULT_HEADERS"
            >
              <template #item="{ item, index }">
                <div style="margin-left: 20px; margin-bottom: 8px; display: flex; align-items: center; gap: 12px;">
                  <el-button
                    type="primary"
                    link
                    size="small"
                    @click="item.showExtractRules = !item.showExtractRules"
                  >
                    {{ item.showExtractRules ? $t('api.label.collapseExtractRules') : $t('api.label.configExtractRules') }}
                    <span
                      v-if="item.extractRules?.length"
                      class="rule-count-badge"
                    >
                      {{ item.extractRules.length }}
                    </span>
                    <common-icon
                      :icon="item.showExtractRules ? 'ArrowDown' : 'ArrowRight'"
                      style="margin-left: 2px"
                    />
                  </el-button>

                  <el-button
                    v-if="item.showExtractRules"
                    type="primary"
                    size="small"
                    link
                    @click="(item.extractRules = item.extractRules || []).push({ enabled: true })"
                  >
                    <common-icon icon="Plus" /> {{ $t('api.label.addExtractRule') }}
                  </el-button>
                </div>

                <el-collapse-transition>
                  <div
                    v-show="item.showExtractRules"
                    style="padding: 12px; margin-bottom: 12px; background-color: var(--el-fill-color-light); margin-left: 20px; margin-right: 20px; border-radius: 4px; border: 1px solid var(--el-border-color-lighter);"
                  >
                    <common-table-form
                      :model="item"
                      data-list-key="extractRules"
                      :form-options="extractRulesOptions"
                      :form-prop-prefix="`envParams.${index}`"
                      sortable
                      @delete="({index: idx}) => item.extractRules.splice(idx, 1)"
                    >
                      <template #jsonPathHeader>
                        <div style="display: flex; align-items: center; white-space: nowrap;">
                          <span style="color: var(--el-color-danger); margin-right: 4px;">*</span>
                          <span>{{ $t('api.label.extractPathFull') }}</span>
                        </div>
                      </template>
                      <template #matchPathSlot="{ item: rule }">
                        <el-form-item
                          v-if="item.extractRules && item.extractRules.indexOf(rule) !== -1"
                          label-width="0"
                          :prop="`envParams.${index}.extractRules.${item.extractRules.indexOf(rule)}.apiPath`"
                          :rules="[{ required: true, message: $i18nBundle('common.msg.nonNull', [$t('api.label.matchPath')]), trigger: ['blur', 'change'] }]"
                        >
                          <el-tooltip
                            effect="dark"
                            :content="rule.apiSummary"
                            :disabled="!rule.apiSummary"
                            placement="top"
                          >
                            <el-input
                              v-model="rule.apiPath"
                              :placeholder="$t('api.msg.matchPathPlaceholder')"
                              :readonly="rule.matchType === 'api'"
                              clearable
                              style="width: 100%;"
                              class="match-path-input"
                              :class="{
                                'is-deprecated': rule.matchType === 'api' && rule.deprecated,
                                'is-disabled-api': rule.matchType === 'api' && rule.enabled === false
                              }"
                            >
                              <template
                                v-if="rule.apiMethod"
                                #prefix
                              >
                                <div style="display: flex; align-items: center; height: 100%;">
                                  <ApiMethodTag
                                    :method="rule.apiMethod"
                                    size="small"
                                    style="margin-right: 4px;"
                                  />
                                </div>
                              </template>
                              <template
                                v-if="rule.matchType === 'api'"
                                #suffix
                              >
                                <el-tooltip
                                  effect="dark"
                                  :content="$t('common.label.clear')"
                                  placement="top"
                                >
                                  <el-icon
                                    class="el-input__icon match-path-clear-btn"
                                    style="cursor: pointer;"
                                    @click.stop="switchToCustomPath(rule)"
                                  >
                                    <common-icon icon="CircleClose" />
                                  </el-icon>
                                </el-tooltip>
                              </template>
                              <template
                                v-if="!isLocalMode"
                                #append
                              >
                                <el-tooltip
                                  effect="dark"
                                  :content="$t('api.label.selectApi')"
                                  placement="top"
                                >
                                  <el-button
                                    @click="openExtractApiTreeSelect(rule)"
                                  >
                                    <common-icon icon="Search" />
                                  </el-button>
                                </el-tooltip>
                              </template>
                            </el-input>
                          </el-tooltip>
                        </el-form-item>
                      </template>
                    </common-table-form>
                  </div>
                </el-collapse-transition>
              </template>
            </common-params-edit>
          </div>
        </el-tab-pane>

        <el-tab-pane
          v-if="!isLocalMode"
          :label="$t('api.label.loginApiConfig')"
          name="loginApi"
        >
          <div style="padding: 12px 4px 20px 4px;">
            <el-alert
              :title="$t('api.msg.loginApiTip')"
              type="info"
              show-icon
              :closable="false"
              class="margin-bottom3"
            />
            <el-form-item
              :label="$t('api.label.selectLoginApi')"
              label-width="120px"
            >
              <div style="width: 100%;">
                <div
                  v-if="groupConfig.loginApiConfigs?.length"
                  ref="loginSortableRef"
                  class="margin-bottom2 flex-column"
                  style="gap: 8px;"
                >
                  <div
                    v-for="(apiConfig, index) in groupConfig.loginApiConfigs"
                    :key="apiConfig.apiId || index"
                    class="login-api-item"
                    style="display: flex; align-items: center; justify-content: space-between; padding: 6px 10px; border: 1px solid var(--el-border-color-lighter); border-radius: 4px; background: var(--el-fill-color-blank);"
                    @mouseenter="hoverLoginIndex = index"
                    @mouseleave="hoverLoginIndex = -1"
                  >
                    <div style="display: flex; align-items: center; gap: 8px; min-width: 0; flex: 1; margin-right: 8px;">
                      <common-icon
                        :size="18"
                        class="move-indicator"
                        icon="DragIndicatorFilled"
                        style="cursor: move; flex-shrink: 0; color: var(--el-text-color-secondary);"
                        :style="{ visibility: hoverLoginIndex === index && !loginDragging ? 'visible' : 'hidden' }"
                      />
                      <ApiMethodTag
                        v-if="apiConfig.method"
                        :method="apiConfig.method"
                        size="small"
                        style="flex-shrink: 0;"
                      />
                      <el-text
                        :type="!apiConfig.enabled ? 'danger' : (apiConfig.deprecated ? 'warning' : '')"
                        style="min-width: 0; display: inline-flex; align-items: center; gap: 8px; flex: 1;"
                      >
                        <component
                          :is="apiConfig.deprecated ? 'del' : 'span'"
                          style="font-weight: 500; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 45%;"
                          :title="apiConfig.summary || apiConfig.url"
                        >
                          {{ apiConfig.summary || apiConfig.url }}
                        </component>
                        <component
                          :is="apiConfig.deprecated ? 'del' : 'span'"
                          v-if="apiConfig.url"
                          class="login-api-url"
                          style="font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1;"
                          :title="apiConfig.url"
                        >
                          {{ apiConfig.url }}
                        </component>
                      </el-text>
                    </div>
                    <el-button
                      type="danger"
                      size="small"
                      circle
                      @click="removeLoginApi(index)"
                    >
                      <common-icon icon="Delete" />
                    </el-button>
                  </div>
                </div>
                <el-button
                  type="primary"
                  size="small"
                  @click="openLoginApiTreeSelect"
                >
                  <common-icon
                    class="margin-right1"
                    icon="Plus"
                  />
                  {{ $t('api.label.selectLoginApi') }}
                </el-button>
              </div>
            </el-form-item>
          </div>
        </el-tab-pane>
      </el-tabs>
    </common-form>
    <TreeConfigWindow
      v-model="showTreeConfigWindow"
      v-model:selected-keys="selectedTreeKeys"
      node-key="id"
      :tree-nodes="docTreeNodes"
      :tree-attrs="{ checkStrictly: true, props: { label: 'label', children: 'children', disabled: isTreeNodeDisabled } }"
      :title="treeConfigContext === 'login' ? $t('api.label.selectLoginApi') : $t('api.label.selectApi')"
      width="950px"
      :single-select="treeConfigContext === 'extract'"
      @submit-keys="handleTreeSelectSubmit"
    >
      <template #default="{ node, data }">
        <el-text
          :type="!data.enabled ? 'danger' : (data.deprecated ? 'warning' : '')"
          class="custom-tree-node"
        >
          <TreeIconLabel
            :show-icon="true"
            :node="node"
            :icon-leaf="calcNodeLeaf(data)"
            :url="data.isDoc ? data.url : ''"
          >
            <ApiMethodTag
              v-if="data.method"
              :method="data.method"
              size="small"
              style="margin-right: 6px;"
            />
            <del v-if="data.deprecated">{{ data.docName || data.folderName || node.label }}</del>
            <span v-else>{{ data.docName || data.folderName || node.label }}</span>
          </TreeIconLabel>
        </el-text>
      </template>
    </TreeConfigWindow>
  </common-window>
</template>

<style scoped>
.match-path-input :deep(.match-path-clear-btn) {
  display: none;
}

.match-path-input:hover :deep(.match-path-clear-btn),
.match-path-input:focus-within :deep(.match-path-clear-btn) {
  display: inline-flex;
}

.match-path-input.is-deprecated :deep(input) {
  color: var(--el-color-warning) !important;
  text-decoration: line-through;
}

.match-path-input.is-disabled-api :deep(input) {
  color: var(--el-color-danger) !important;
}

.rule-count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  font-size: 11px;
  line-height: 1;
  font-weight: 600;
  border-radius: 8px;
  color: #fff;
  background-color: var(--el-color-primary);
  margin-left: 4px;
  margin-right: 2px;
}

.login-api-url {
  color: var(--el-text-color-secondary);
}

.el-text--danger .login-api-url,
.el-text--warning .login-api-url {
  color: inherit;
  opacity: 0.85;
}
</style>
