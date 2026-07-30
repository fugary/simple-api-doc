<script setup>
import { computed, ref } from 'vue'
import { cloneDeep } from 'lodash-es'
import ApiProjectApi, { loadDetailById } from '@/api/ApiProjectApi'
import CommonParamsEdit from '@/views/components/utils/CommonParamsEdit.vue'
import { ElAlert, ElFormItem, ElMessage, ElTreeSelect, ElTabPane, ElTabs } from 'element-plus'
import { $i18nBundle } from '@/messages'
import { DEFAULT_HEADERS } from '@/consts/ApiConstants'
import { defineFormOptions } from '@/components/utils'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import CommonIcon from '@/components/common-icon/index.vue'
import { calcProjectItem } from '@/services/api/ApiProjectService'

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
  required: true,
  tooltipKey: 'api.msg.matchPathTip',
  minWidth: 150
}, {
  labelKey: 'api.label.extractPath',
  prop: 'jsonPath',
  required: true,
  tooltipKey: 'api.msg.extractPathTip',
  minWidth: 180,
  headerSlot: 'jsonPathHeader'
}])

const showWindow = ref(false)
const projectItem = ref()
const isLocalMode = ref(false)
const preferenceIdRef = ref('')
const shareConfigStore = useShareConfigStore()
const activeTab = ref('envParams')
const docTreeNodes = ref([])
const selectedLoginApiId = ref('')

const isSavedLocal = computed(() => {
  return isLocalMode.value && !!(preferenceIdRef.value && shareConfigStore.getLocalEnvParams(preferenceIdRef.value)?.length)
})

const groupConfig = ref({
  envParams: [],
  loginApiConfig: null
})

const isTreeNodeDisabled = (nodeData) => {
  return !nodeData.isDoc
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
      docTreeNodes.value = calcRes?.docTreeNodes || []
    } else {
      docTreeNodes.value = []
    }
    const dbConfig = projectItem.value?.groupConfig ? JSON.parse(projectItem.value.groupConfig) : { envParams: [], loginApiConfig: null }
    if (isLocalMode.value) {
      const localParams = shareConfigStore.getLocalEnvParams(preferenceIdRef.value)
      groupConfig.value = {
        ...dbConfig,
        envParams: (localParams && localParams.length > 0) ? cloneDeep(localParams) : (dbConfig.envParams || []),
        loginApiConfig: dbConfig.loginApiConfig || null
      }
    } else {
      groupConfig.value = dbConfig
    }
    selectedLoginApiId.value = groupConfig.value?.loginApiConfig?.apiId || ''
    showWindow.value = true
  })
  return new Promise(resolve => (callback = resolve))
}

const handleLoginApiChange = (val) => {
  if (!val) {
    groupConfig.value.loginApiConfig = null
  } else {
    const found = findDocNodeInTree(docTreeNodes.value, val)
    if (found) {
      groupConfig.value.loginApiConfig = {
        apiId: found.id,
        url: found.url,
        method: found.method,
        summary: found.docName || found.label || found.url
      }
    }
  }
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
    const dbConfig = projectItem.value?.groupConfig ? JSON.parse(projectItem.value.groupConfig) : { envParams: [], loginApiConfig: null }
    groupConfig.value = cloneDeep(dbConfig)
    selectedLoginApiId.value = groupConfig.value?.loginApiConfig?.apiId || ''
    ElMessage.success($i18nBundle('common.msg.operationSuccess'))
  }
}

const saveGroupConfig = ({ form }) => {
  form.validate(valid => {
    if (valid) {
      const configToSave = cloneDeep(groupConfig.value)
      configToSave.envParams?.forEach(param => {
        delete param.showExtractRules
      })
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
      }
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
    width="900px"
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
                      @delete="({index: idx}) => item.extractRules.splice(idx, 1)"
                    >
                      <template #jsonPathHeader>
                        <div style="display: flex; align-items: center;">
                          <span style="color: var(--el-color-danger); margin-right: 4px;">*</span>
                          <span>{{ $t('api.label.extractPathFull') }}</span>
                        </div>
                      </template>
                    </common-table-form>
                  </div>
                </el-collapse-transition>
              </template>
            </common-params-edit>
          </div>
        </el-tab-pane>

        <el-tab-pane
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
              <el-tree-select
                v-model="selectedLoginApiId"
                :data="docTreeNodes"
                node-key="id"
                :props="{ label: 'label', children: 'children', disabled: isTreeNodeDisabled }"
                clearable
                filterable
                check-strictly
                style="width: 100%"
                :placeholder="$t('common.label.pleaseSelect')"
                @change="handleLoginApiChange"
              >
                <template #default="{ data }">
                  <div style="display: flex; align-items: center; justify-content: space-between; width: 100%; padding-right: 8px;">
                    <span v-if="data.isDoc">
                      <ApiMethodTag
                        :method="data.method"
                        size="small"
                        style="margin-right: 8px;"
                      />
                      <span>{{ data.docName || data.label }}</span>
                    </span>
                    <span
                      v-else
                      style="display: flex; align-items: center;"
                    >
                      <CommonIcon
                        icon="Folder"
                        size="16"
                        style="margin-right: 6px; color: var(--el-color-primary);"
                      />
                      <span style="font-weight: 500;">{{ data.folderName || data.label }}</span>
                    </span>
                    <span
                      v-if="data.isDoc"
                      style="color: var(--el-text-color-secondary); font-size: 12px; margin-left: 16px;"
                    >
                      {{ data.url }}
                    </span>
                  </div>
                </template>
              </el-tree-select>
            </el-form-item>
          </div>
        </el-tab-pane>
      </el-tabs>
    </common-form>
  </common-window>
</template>

<style scoped>
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
</style>
