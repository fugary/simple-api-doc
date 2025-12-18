<script setup lang="jsx">
import { $i18nBundle, $i18nKey, $i18nMsg } from '@/messages'
import { computed, inject, reactive, ref, watch } from 'vue'
import { useMonacoEditorOptions } from '@/vendors/monaco-editor'
import UrlCopyLink from '@/views/components/api/UrlCopyLink.vue'
import { ElText } from 'element-plus'
import { $coreConfirm, $coreError, getSingleSelectOptions, getStyleGrow } from '@/utils'
import ApiComponentSchemaEditTree from '@/views/components/api/project/schema/ApiComponentSchemaEditTree.vue'
import ApiProjectInfoDetailApi, { copyApiModel, loadInfoDetail } from '@/api/ApiProjectInfoDetailApi'
import { inProjectCheckAccess } from '@/api/ApiProjectGroupApi'
import { ALL_CONTENT_TYPES, ALL_STATUS_CODES, AUTHORITY_TYPE, SCHEMA_COMPONENT_PREFIX } from '@/consts/ApiConstants'
import { loadDetailById } from '@/api/ApiProjectApi'
import { useManagedArrayItems } from '@/hooks/CommonHooks'
import { checkAndSaveDocInfoDetail, processProjectInfos, useComponentSchemas } from '@/services/api/ApiDocEditService'
import { showMarkdownWindow } from '@/utils/DynamicUtils'
import ApiSchemaHistoryViewer from '@/views/components/api/doc/comp/ApiSchemaHistoryViewer.vue'

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
const { componentSchemas, loadComponents } = useComponentSchemas()
const currentComponentModel = ref()
const { managedItems, pushItem, clearItems, startContext, goToItem } = useManagedArrayItems()
const historyCount = ref(0)
watch(currentInfoDetail, async model => {
  historyCount.value = 0
  if (model.id) {
    await loadInfoDetail(model).then(data => {
      currentComponentModel.value = data.resultData
      componentSchemas.value = data.addons?.components || []
      historyCount.value = data.addons?.historyCount || 0
    })
  } else {
    currentComponentModel.value = currentInfoDetail.value
    if (!componentSchemas.value.length && currentInfoDetail.value?.schemaContent?.includes(SCHEMA_COMPONENT_PREFIX)) {
      await loadComponents(currentInfoDetail.value)
    }
  }
  const projectId = currentComponentModel.value.projectId
  console.log('=============================data', currentComponentModel.value, projectItemMap, projectId)
  if (!projectItemMap[projectId]) {
    loadDetailById(projectId)
      .then((data) => {
        projectItemMap[projectId] = data
      })
  }
  clearItems()
  pushItem(currentComponentModel.value)
}, { immediate: true })
const projectItem = computed(() => projectItemMap[currentComponentModel.value?.projectId])
const deletable = computed(() => inProjectCheckAccess(projectItem.value, AUTHORITY_TYPE.DELETABLE))
const writable = computed(() => inProjectCheckAccess(projectItem.value, AUTHORITY_TYPE.WRITABLE) || deletable.value)
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
        title: $i18nKey('common.label.commonEdit', 'common.label.description')
      }, {
        'onUpdate:modelValue': value => {
          if (currentComponentModel.value) {
            currentComponentModel.value.description = value
          }
        }
      })
    }
  }]
})

const emit = defineEmits(['saveComponent', 'deleteComponent', 'copyComponent'])
const saveComponent = (form) => {
  form.validate((valid) => {
    if (valid) {
      currentComponentModel.value.schemaContent = contentRef.value
      const data = currentComponentModel.value
      if (data.schemaContent) {
        checkAndSaveDocInfoDetail(data)
          .then((resultData) => emit('saveComponent', resultData))
      } else {
        $coreError($i18nBundle('common.msg.nonNull', ['JSON Schema']))
      }
    }
  })
}
const deleteComponent = () => {
  return $coreConfirm($i18nBundle('common.msg.deleteConfirm')).then(() => {
    return ApiProjectInfoDetailApi.deleteById(currentComponentModel.value.id)
      .then(() => {
        emit('deleteComponent', currentComponentModel.value)
      })
  })
}
const copyComponent = () => {
  return $coreConfirm($i18nBundle('common.msg.confirmCopy'))
    .then(() => copyApiModel(currentComponentModel.value.id))
    .then(result => {
      if (result.resultData?.id) {
        emit('saveComponent', result.resultData)
        gotoComponent(result.resultData)
      }
    })
}
const { contentRef, languageRef, editorRef, monacoEditorOptions, languageModel, languageSelectOption, formatDocument } = useMonacoEditorOptions({ readOnly: false })
languageRef.value = 'json'
const schemaContentObj = ref({})
const mediaTypeSchemaObj = ref()
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
    schemaContentObj.value = schemaObj
  } catch (e) { // 忽略错误保证能正常显示
    console.error(e)
  }
}, { immediate: true })
watch(schemaContentObj, () => {
  if (currentComponentModel.value) {
    let schemaContent = JSON.stringify(schemaContentObj.value)
    if (mediaTypeSchemaObj.value) {
      mediaTypeSchemaObj.value.schema = schemaContentObj.value
      schemaContent = JSON.stringify(mediaTypeSchemaObj.value)
    }
    currentComponentModel.value.schemaContent = schemaContent
  }
}, { deep: true })
const codeHeight = '400px'

const currentProjectInfo = computed(() => {
  return projectInfos.value?.find(info => info.id === currentComponentModel.value.infoId)
})

const gotoComponent = (componentData) => {
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

const apiHistoryRef = ref()

defineExpose({
  saveComponent,
  copyComponent,
  deleteComponent
})
</script>

<template>
  <el-container class="flex-column height100">
    <common-form
      v-if="currentComponentModel&&schemaContentObj"
      class="padding-left2 padding-right2"
      :class="{'padding-top3':!inWindow}"
      class-name="common-form-auto"
      :model="currentComponentModel"
      inline
      :options="componentEditOptions"
      :show-buttons="false"
    >
      <template
        v-if="(writable && !inWindow) || historyCount"
        #after-buttons="{form}"
      >
        <el-container class="flex-center">
          <el-link
            v-if="historyCount"
            class="margin-right1"
            type="primary"
            @click="apiHistoryRef?.showHistoryList(currentInfoDetail.id)"
          >
            {{ $t('api.label.historyVersions') }}
            <el-text type="info">
              ({{ historyCount }})
            </el-text>
          </el-link>
          <template v-if="writable&&!inWindow">
            <el-button
              type="primary"
              @click="saveComponent(form)"
            >
              {{ $t('common.label.save') }}
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
        </el-container>
      </template>
    </common-form>
    <el-container class="padding-10 flex-column">
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
            @click="gotoComponent(goToItem(index))"
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
      <el-tabs>
        <el-tab-pane
          :label="$t('api.label.dataModel')"
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
              @goto-component="gotoComponent"
            />
          </el-container>
        </el-tab-pane>
        <el-tab-pane
          label="JSON Schema"
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
            :options="monacoEditorOptions"
            class="common-resize-vertical"
            @mount="editorRef=$event"
          />
        </el-tab-pane>
      </el-tabs>
    </el-container>
    <api-schema-history-viewer
      v-if="historyCount"
      ref="apiHistoryRef"
      :editable="writable"
      @update-history="$emit('saveComponent', $event)"
    />
  </el-container>
</template>

<style scoped>

</style>
