<script setup lang="jsx">
import { $i18nBundle, $i18nKey, $i18nMsg } from '@/messages'
import { computed, inject, reactive, ref, watch } from 'vue'
import { useMonacoEditorOptions } from '@/vendors/monaco-editor'
import { useEditDraft } from '@/hooks/useEditDraft'
import { modelDraftFields, modelDraftSignature, normalizeSchemaContent } from '@/services/api/DocDraftStorage'
import DocDraftWindow from '@/views/components/api/doc/comp/DocDraftWindow.vue'
import DocDraftNotice from '@/views/components/api/doc/comp/DocDraftNotice.vue'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import UrlCopyLink from '@/views/components/api/UrlCopyLink.vue'
import { ElMessage, ElTag, ElText } from 'element-plus'
import { $copyText, $coreConfirm, $coreError, formatDate, getSingleSelectOptions, getStyleGrow } from '@/utils'
import ApiDocSchemaTable from '@/views/components/api/doc/comp/ApiDocSchemaTable.vue'
import ApiComponentSchemaEditTree from '@/views/components/api/project/schema/ApiComponentSchemaEditTree.vue'
import ApiProjectInfoDetailApi, { copyApiModel, loadInfoDetail, loadInfoDetails, loadHistoryDiff, loadHistoryList, recoverFromHistory } from '@/api/ApiProjectInfoDetailApi'
import { inProjectCheckAccess } from '@/api/ApiProjectGroupApi'
import { ALL_CONTENT_TYPES, ALL_STATUS_CODES, AUTHORITY_TYPE } from '@/consts/ApiConstants'
import { loadDetailById } from '@/api/ApiProjectApi'
import { useManagedArrayItems } from '@/hooks/CommonHooks'
import {
  checkAndSaveDocInfoDetail,
  cleanSchemaExamples,
  processProjectInfos
} from '@/services/api/ApiDocEditService'
import { showHistoryListWindow, showMarkdownWindow, showApiCompareWindow } from '@/utils/DynamicUtils'
import { defineFormOptions, defineTableColumns, renderSelectOptionLabel } from '@/components/utils'
import { getComponentHistoryViewOptions } from '@/services/api/ApiDocPreviewService'
import CommonIcon from '@/components/common-icon/index.vue'
import { generateModel, getAiStatus } from '@/api/AiCacheApi'
import { buildAiConfigOptions, useAiModelSelector } from '@/services/api/ApiCommonService'

const props = defineProps({
  currentProject: {
    type: Object,
    default: null
  }
})
const inWindow = inject('commonWindow', null)
const currentInfoDetail = defineModel({
  type: Object,
  required: true
})
const projectItemMap = reactive({})
props.currentProject && (projectItemMap[props.currentProject.id] = props.currentProject)
const componentSchemas = ref([])
const { managedItems, pushItem, clearItems, startContext, goToItem } = useManagedArrayItems()
const historyCount = ref(0)
const { model: currentComponentModel, saving, draft, onSaved } = useEditDraft(currentInfoDetail, {
  type: 'model',
  signature: modelDraftSignature,
  fields: model => model?.bodyType === 'component' ? modelDraftFields(model) : {},
  enabled: model => model.bodyType === 'component' && writable.value,
  project: model => model.projectId,
  async load (model) {
    const result = model.id ? await loadInfoDetail(model) : { success: true, resultData: { ...model } }
    if (!result.success || !result.resultData) throw new Error('Model unavailable')
    const value = result.resultData
    const [project, components] = await Promise.all([
      projectItemMap[value.projectId] || loadDetailById(value.projectId),
      result.addons?.components || loadInfoDetails({ projectId: value.projectId, infoId: value.infoId, bodyType: 'component' })
    ])
    return { ...value, draftContext: { project, components, historyCount: result.addons?.historyCount || 0 } }
  },
  loaded (model) {
    const { project, components, historyCount: count } = model.draftContext
    delete model.draftContext
    projectItemMap[model.projectId] = project
    historyCount.value = count
    componentSchemas.value = components || []
    clearItems()
    pushItem(model)
  },
  saved: data => emit('saveComponent', data)
})
const cancelDraftEditing = () => {
  if (inWindow) inWindow.value.showWindow = false
  else currentInfoDetail.value = null
}
const projectItem = computed(() => projectItemMap[currentComponentModel.value?.projectId])
const deletable = computed(() => inProjectCheckAccess(projectItem.value, AUTHORITY_TYPE.DELETABLE))
const writable = computed(() => inProjectCheckAccess(projectItem.value, AUTHORITY_TYPE.WRITABLE) || deletable.value)
const readonly = computed(() => !writable.value)
const projectInfos = computed(() => {
  if (projectItem.value?.infoList?.length) {
    return processProjectInfos(projectItem.value)
  }
  return []
})
const componentEditOptions = computed(() => {
  const statusOptions = ALL_STATUS_CODES.map(status => {
    const label = $i18nMsg(`${status.labelCn} - ${(status.labelEn)}`, `${status.labelEn} - ${(status.labelCn)}`)
    return {
      value: status.code,
      label: `${status.code} - ${label}`
    }
  })
  const bodyType = currentComponentModel.value?.bodyType
  const modelLabelKey = bodyType ? `api.label.${currentComponentModel.value?.bodyType}Name` : 'api.label.componentName'
  return [{
    labelKey: modelLabelKey,
    prop: 'schemaName',
    placeholder: $i18nKey('common.msg.commonInput', modelLabelKey),
    required: bodyType !== 'request',
    style: getStyleGrow(10),
    attrs: {
      size: 'large'
    }
  }, {
    labelKey: 'api.label.apiDocLock',
    type: 'switch',
    prop: 'locked',
    tooltip: $i18nBundle('api.msg.apiDocLocked'),
    enabled: !currentComponentModel.value?.docId,
    style: getStyleGrow(3)
  }, {
    enabled: projectInfos.value?.length > 1,
    labelKey: 'api.label.importFolder',
    prop: 'infoId',
    placeholder: $i18nKey('common.label.commonSelect', 'api.label.importFolder'),
    disabled: !!currentComponentModel.value?.id,
    required: true,
    type: 'select',
    children: projectInfos.value?.map(info => ({
      label: info.folderName,
      value: info.id
    })),
    attrs: {
      clearable: false,
      style: {
        minWidth: '100px'
      }
    },
    style: getStyleGrow(3)
  }, {
    label: 'Content Type',
    prop: 'contentType',
    type: 'select',
    enabled: ['request', 'response'].includes(bodyType),
    children: getSingleSelectOptions(...ALL_CONTENT_TYPES),
    style: getStyleGrow(3),
    attrs: {
      filterable: true
    }
  }, {
    labelKey: 'api.label.statusCode',
    prop: 'statusCode',
    type: 'select',
    enabled: bodyType === 'response',
    children: statusOptions,
    style: getStyleGrow(3),
    attrs: {
      filterable: true
    }
  }, {
    labelKey: 'common.label.description',
    placeholder: $i18nKey('common.msg.commonInput', 'common.label.description'),
    prop: 'description',
    style: getStyleGrow(10),
    attrs: {
      type: 'textarea'
    },
    tooltip: $i18nBundle('common.label.newWindowEdit'),
    tooltipIcon: 'EditPen',
    tooltipLinkAttrs: {
      type: 'primary'
    },
    tooltipFunc () {
      showMarkdownWindow({
        content: currentComponentModel.value?.description,
        title: $i18nKey('common.label.commonEdit', 'common.label.description'),
        readonly: readonly.value
      }, {
        'onUpdate:modelValue': value => {
          if (!readonly.value && currentComponentModel.value) {
            currentComponentModel.value.description = value
          }
        }
      })
    }
  }]
})

const emit = defineEmits(['saveComponent', 'deleteComponent', 'copyComponent'])
const saveComponent = async (form) => {
  if (saving.value || readonly.value || draft.pending || draft.loadError) return false
  saving.value = true
  try {
    if (!await form.validate().catch(() => false)) return false
    const data = { ...currentComponentModel.value, schemaContent: contentRef.value }
    if (!data.schemaContent) {
      $coreError($i18nBundle('common.msg.nonNull', ['JSON Schema']))
      return false
    }
    const resultData = await checkAndSaveDocInfoDetail(data)
    if (!resultData) return false
    Object.assign(currentComponentModel.value, resultData)
    onSaved(resultData)
    return true
  } catch {
    return false
  } finally {
    saving.value = false
  }
}
const deleteComponent = async () => {
  if (!await draft.confirmLeave()) return false
  return $coreConfirm($i18nBundle('common.msg.deleteConfirm')).then(() => {
    return ApiProjectInfoDetailApi.deleteById(currentComponentModel.value.id)
      .then(() => {
        draft.discard()
        emit('deleteComponent', currentComponentModel.value)
        return true
      })
  })
}
const copyComponent = async () => {
  if (!await draft.confirmLeave()) return
  return $coreConfirm($i18nBundle('common.msg.confirmCopy'))
    .then(() => copyApiModel(currentComponentModel.value.id, { loading: true }))
    .then(result => {
      if (result.resultData?.id) {
        startContext()
        currentInfoDetail.value = result.resultData
        emit('saveComponent', result.resultData)
      }
    })
}
const { contentRef, languageRef, editorRef, monacoEditorOptions, languageModel, languageSelectOption, formatDocument } = useMonacoEditorOptions({ readOnly: false })
watch(() => readonly.value || saving.value || !!draft.pending, isReadOnly => {
  monacoEditorOptions.readOnly = isReadOnly
  if (editorRef.value) {
    editorRef.value.updateOptions({ readOnly: isReadOnly })
  }
}, { immediate: true })
languageRef.value = 'json'
const schemaContentObj = ref({})
const mediaTypeSchemaObj = ref()
const activeTab = ref('model')
watch(() => currentComponentModel.value?.schemaContent, (schemaContent) => {
  contentRef.value = schemaContent
  try {
    const isMediaType = !!currentComponentModel.value?.docId
    const defaultContent = isMediaType ? '{"schema": {}}' : '{}'
    let schemaObj = JSON.parse(schemaContent || defaultContent)
    mediaTypeSchemaObj.value = null
    if (schemaObj?.schema) { // 如果是mediaType，特殊处理
      mediaTypeSchemaObj.value = schemaObj
      schemaObj = schemaObj.schema
    }
    if (JSON.stringify(schemaContentObj.value) !== JSON.stringify(schemaObj)) schemaContentObj.value = schemaObj
  } catch { // 保留未完成的源码并切换到源码编辑
    activeTab.value = 'schema'
  }
}, { immediate: true, flush: 'sync' })
watch(schemaContentObj, () => {
  if (currentComponentModel.value) {
    let schemaContent = JSON.stringify(schemaContentObj.value)
    if (mediaTypeSchemaObj.value) {
      mediaTypeSchemaObj.value.schema = schemaContentObj.value
      schemaContent = JSON.stringify(mediaTypeSchemaObj.value)
    }
    // 解析源码时不反向格式化，保留原文以及未完成的 JSON。
    if (normalizeSchemaContent(currentComponentModel.value.schemaContent) === schemaContent) return
    currentComponentModel.value.schemaContent = schemaContent
  }
}, { deep: true, flush: 'sync' })
watch(contentRef, value => {
  if (currentComponentModel.value) currentComponentModel.value.schemaContent = value
}, { flush: 'sync' })
const codeHeight = '400px'
const currentProjectInfo = computed(() => {
  return projectInfos.value?.find(info => info.id === currentComponentModel.value.infoId)
})

const gotoComponent = async (componentData, index) => {
  if (!await draft.confirmLeave()) return
  if (index !== undefined) componentData = goToItem(index)
  startContext()
  currentInfoDetail.value = componentData
}

const getSchemaNameLabel = item => {
  if (!item.schemaName) {
    if (item.bodyType === 'request') {
      return $i18nBundle('api.label.requestBody')
    } else if (item.bodyType === 'response') {
      return $i18nBundle('api.label.responseBody')
    }
  }
  return item.schemaName || 'root'
}

const toShowHistoryWindow = (current) => {
  const limit = 300
  const emptyDoc = { bodyType: current.bodyType }
  const bodyType = current?.bodyType
  const modelLabelKey = bodyType ? `api.label.${bodyType}Name` : 'api.label.componentName'
  showHistoryListWindow({
    columns: defineTableColumns([{
      labelKey: modelLabelKey,
      property: 'schemaName'
    }, {
      label: 'JSON Schema',
      formatter (data) {
        let tooltip = data.schemaContent
        if (data.schemaContent?.length && data.schemaContent.length > limit) {
          tooltip = data.schemaContent?.substring(0, limit) + '...'
        }
        return <ElText v-common-tooltip={tooltip}
                       onClick={() => $copyText(data.schemaContent)}
                       style="white-space: nowrap;cursor: pointer;">
          {data.schemaContent}
        </ElText>
      }
    }, {
      labelKey: 'api.label.lockStatus',
      formatter (data) {
        let lockStatus = <></>
        if (data.locked) {
          lockStatus = <CommonIcon icon="LockFilled" size={18} class="margin-left1"
                                   style="vertical-align: middle;"
                                   v-common-tooltip={$i18nBundle('api.msg.apiDocLocked')}/>
        }
        return <>
          {lockStatus}
        </>
      },
      attrs: {
        align: 'center'
      }
    }, {
      labelKey: 'common.label.version',
      formatter (data) {
        const currentFlag = data.isCurrent ? <ElTag type="success" round={true}>{$i18nBundle('api.label.current')}</ElTag> : ''
        return <>
          <span class="margin-right2">{data.version}</span>
          {currentFlag}
        </>
      }
    }, {
      labelKey: 'common.label.modifier',
      formatter (data) {
        return <ElText>{data.modifier || data.creator}</ElText>
      },
      attrs: {
        align: 'center'
      }
    }, {
      labelKey: 'common.label.modifyDate',
      formatter (data) {
        return formatDate(data.modifyDate || data.createDate)
      }
    }]),
    searchFunc: param => loadHistoryList({ ...param, queryId: current.id }),
    compareFunc: async (modified, target, previous) => {
      let original = modified
      if (previous) {
        await loadHistoryDiff({
          queryId: modified.id,
          version: modified.version
        }).then(data => {
          modified = data.resultData?.modifiedDoc || emptyDoc
          original = data.resultData?.originalDoc || emptyDoc
          modified.current = !modified.modifyFrom
        })
      } else {
        modified = target
      }
      showApiCompareWindow({
        modified,
        original,
        historyOptionsMethod: getComponentHistoryViewOptions
      })
    },
    recoverFunc: writable.value ? recoverFromHistory : null,
    onUpdateHistory: async data => {
      if (await draft.confirmLeave()) emit('saveComponent', data)
    }
  })
}

const aiEnabled = ref(false)
const aiConfigs = ref([])
const defaultAiConfigId = ref(null)
const aiDialogVisible = ref(false)
const aiFormModel = ref({ prompt: '', configId: null, model: '', withExample: true })

const { syncModelFromConfig, buildModelFormOption } = useAiModelSelector(aiFormModel, aiConfigs)

getAiStatus().then(res => {
  if (res && res.success) {
    const data = res.resultData || {}
    aiEnabled.value = data.enabled ?? !!data
    aiConfigs.value = data.configs || []
    defaultAiConfigId.value = data.defaultConfigId || null
    if (!aiFormModel.value.configId || !aiConfigs.value.some(c => c.id === aiFormModel.value.configId)) {
      aiFormModel.value.configId = defaultAiConfigId.value
      syncModelFromConfig(aiFormModel.value.configId)
    }
  }
}).catch(console.error)
const aiFormOptions = computed(() => {
  const options = []
  if (aiConfigs.value.length > 0) {
    options.push({
      prop: 'configId',
      labelKey: 'api.label.aiConfigSelect',
      type: 'select',
      children: buildAiConfigOptions(aiConfigs.value, defaultAiConfigId.value),
      slots: { label: renderSelectOptionLabel },
      attrs: {
        clearable: false
      }
    })
    options.push(buildModelFormOption())
  }
  options.push({
    prop: 'withExample',
    labelKey: 'api.msg.aiWithExample',
    type: 'switch'
  })
  options.push({
    prop: 'prompt',
    labelKey: 'api.msg.aiGenerateModelPrompt',
    placeholder: $i18nBundle('api.msg.aiGenerateModelPromptPlaceholder'),
    attrs: {
      type: 'textarea',
      rows: 6
    }
  })

  return defineFormOptions(options)
})

const openAiDialog = () => {
  if (!aiFormModel.value.configId || !aiConfigs.value.some(c => c.id === aiFormModel.value.configId)) {
    aiFormModel.value.configId = defaultAiConfigId.value
  }
  syncModelFromConfig(aiFormModel.value.configId)
  aiDialogVisible.value = true
}

const doGenerateModel = async () => {
  if (!aiFormModel.value.prompt?.trim()) {
    ElMessage.warning($i18nBundle('api.msg.aiGenerateModelInputRequired'))
    return false
  }
  const isNotEmpty = currentComponentModel.value?.schemaName || currentComponentModel.value?.description || currentComponentModel.value?.id
  if (isNotEmpty) {
    try {
      await $coreConfirm($i18nBundle('api.msg.aiGenerateModelOverwriteConfirm'))
    } catch {
      return false
    }
  }
  try {
    const res = await generateModel({
      prompt: aiFormModel.value.prompt.trim(),
      projectId: currentComponentModel.value?.projectId,
      configId: aiFormModel.value.configId,
      model: aiFormModel.value.model || undefined,
      withExample: aiFormModel.value.withExample,
      lang: useGlobalConfigStore().currentLocale
    }, { loading: true, timeout: 60000 })
    if (res?.success && res.resultData) {
      const jsonStr = res.resultData.replace(/^[^{]*/, '').replace(/[^}]*$/, '')
      const { schemaName, description, schema } = JSON.parse(jsonStr) || {}
      if (schemaName) currentComponentModel.value.schemaName = schemaName
      if (description) currentComponentModel.value.description = description
      if (schema) {
        if (!aiFormModel.value.withExample) {
          cleanSchemaExamples(schema)
        }
        schemaContentObj.value = schema
      }
      ElMessage.success($i18nBundle('api.msg.aiGenerateModelSuccess'))
      aiDialogVisible.value = false
    }
  } catch (e) {
    console.error(e)
  }
  return false
}

defineExpose({
  saveComponent,
  copyComponent,
  deleteComponent,
  draft,
  saving
})
</script>

<template>
  <el-container
    v-loading="draft.loading"
    class="flex-column height100"
  >
    <doc-draft-window
      :draft="draft"
      @cancel="cancelDraftEditing"
    />
    <doc-draft-notice
      v-if="draft.loadError"
      :draft="draft"
    />
    <common-form
      v-if="currentComponentModel&&schemaContentObj"
      class="padding-left2 padding-right2"
      :class="{'padding-top3':!inWindow}"
      class-name="common-form-auto"
      :model="currentComponentModel"
      :disabled="readonly || saving || !!draft.pending"
      inline
      :options="componentEditOptions"
      :show-buttons="false"
    >
      <template
        v-if="writable || historyCount"
        #after-buttons="{form}"
      >
        <el-container class="flex-center">
          <el-link
            v-if="historyCount"
            class="margin-right1"
            type="primary"
            @click="toShowHistoryWindow(currentInfoDetail)"
          >
            {{ $t('api.label.historyVersions') }}
            <el-text type="info">
              ({{ historyCount }})
            </el-text>
          </el-link>
          <template v-if="writable&&!inWindow">
            <el-button
              type="primary"
              :loading="saving"
              :disabled="!!draft.pending"
              @click="saveComponent(form)"
            >
              {{ $t('common.label.save') }}
            </el-button>
            <el-button
              v-if="aiEnabled && !currentComponentModel?.id"
              type="success"
              @click="openAiDialog"
            >
              {{ $t('api.label.aiGenerateModel') }}
            </el-button>

            <el-button
              v-if="deletable&&currentComponentModel.id"
              type="danger"
              @click="deleteComponent"
            >
              {{ $t('common.label.delete') }}
            </el-button>
            <el-button
              v-if="currentComponentModel.id"
              type="warning"
              @click="copyComponent"
            >
              {{ $t('common.label.copy') }}
            </el-button>
          </template>
          <doc-draft-notice
            v-if="writable"
            :draft="draft"
          />
        </el-container>
      </template>
    </common-form>
    <el-container
      v-if="currentComponentModel"
      class="padding-10 flex-column"
    >
      <div
        v-if="managedItems?.length>1"
        class="padding-top5 padding-left2 padding-bottom1"
      >
        <template
          v-for="(item, index) in managedItems"
          :key="index"
        >
          <el-link
            v-if="index<managedItems.length-1"
            type="primary"
            style="vertical-align: baseline;"
            @click="gotoComponent(item, index)"
          >
            {{ getSchemaNameLabel(item) }}
          </el-link>
          <el-text
            v-else
            type="info"
            tag="b"
          >
            {{ getSchemaNameLabel(item) }}
          </el-text>
          <el-text
            v-if="index<managedItems.length-1"
            type="info"
          >
            &nbsp;/&nbsp;
          </el-text>
        </template>
      </div>
      <el-tabs v-model="activeTab">
        <el-tab-pane
          :label="$t('api.label.dataModel')"
          name="model"
          lazy
        >
          <el-container
            class="flex-column"
          >
            <api-component-schema-edit-tree
              v-if="currentComponentModel&&schemaContentObj"
              v-model="schemaContentObj"
              :current-info-detail="currentComponentModel"
              :root-name="getSchemaNameLabel(currentComponentModel)"
              :spec-version="currentProjectInfo?.specVersion"
              :component-schemas="componentSchemas"
              :show-merge-all-of="false"
              :readonly="readonly || saving || !!draft.pending"
              @goto-component="gotoComponent"
            />
          </el-container>
        </el-tab-pane>
        <el-tab-pane
          label="JSON Schema"
          name="schema"
          lazy
        >
          <common-form-control
            class="form-edit-width-90"
            :model="languageModel"
            :option="languageSelectOption"
          >
            <template #childAfter>
              <url-copy-link
                :content="currentComponentModel.schemaContent"
                :tooltip="$i18nKey('common.label.commonCopy', 'common.label.code')"
              />
              <el-link
                v-common-tooltip="$i18nKey('common.label.commonFormat', 'common.label.code')"
                type="primary"
                underline="never"
                class="margin-left3"
                @click="formatDocument"
              >
                <common-icon
                  :size="18"
                  icon="FormatIndentIncreaseFilled"
                />
              </el-link>
              <el-link
                v-common-tooltip="$t('api.msg.showRawData')"
                type="primary"
                underline="never"
                class="margin-left2"
                @click="contentRef=currentComponentModel.schemaContent"
              >
                <common-icon
                  :size="40"
                  icon="RawOnFilled"
                />
              </el-link>
            </template>
          </common-form-control>
          <vue-monaco-editor
            v-model:value="contentRef"
            :language="languageRef"
            :height="codeHeight"
            :theme="useGlobalConfigStore().monacoTheme"
            :options="monacoEditorOptions"
            class="common-resize-vertical"
            @mount="editorRef=$event"
          />
        </el-tab-pane>
        <el-tab-pane
          :label="$t('api.label.schemaPreview')"
          name="table"
          lazy
        >
          <api-doc-schema-table
            v-if="activeTab === 'table' && currentComponentModel && schemaContentObj"
            :schema="schemaContentObj"
            :schema-name="getSchemaNameLabel(currentComponentModel)"
            :spec-version="currentProjectInfo?.specVersion"
            :component-schemas="componentSchemas"
          />
        </el-tab-pane>
      </el-tabs>
    </el-container>
  </el-container>
  <common-window
    v-model="aiDialogVisible"
    :title="$t('api.label.aiGenerateModel')"
    width="800px"
    :ok-click="doGenerateModel"
  >
    <common-form
      :model="aiFormModel"
      :options="aiFormOptions"
      label-width="150px"
      class="form-edit-width-100"
      :show-buttons="false"
    />
  </common-window>
</template>

<style scoped>

</style>
