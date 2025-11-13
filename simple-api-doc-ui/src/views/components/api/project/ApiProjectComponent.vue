<script setup lang="jsx">
import { $i18nBundle, $i18nKey } from '@/messages'
import { computed, inject, reactive, ref, watch } from 'vue'
import { useMonacoEditorOptions } from '@/vendors/monaco-editor'
import UrlCopyLink from '@/views/components/api/UrlCopyLink.vue'
import { ElMessage, ElText } from 'element-plus'
import { $coreConfirm, $coreError } from '@/utils'
import ApiComponentSchemaEditTree from '@/views/components/api/project/schema/ApiComponentSchemaEditTree.vue'
import ApiProjectInfoDetailApi, { loadInfoDetail } from '@/api/ApiProjectInfoDetailApi'
import { inProjectCheckAccess } from '@/api/ApiProjectGroupApi'
import { AUTHORITY_TYPE } from '@/consts/ApiConstants'
import { loadDetailById } from '@/api/ApiProjectApi'
import { useManagedArrayItems } from '@/hooks/CommonHooks'

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
const currentComponentModel = ref()
const { managedItems, pushItem, clearItems, startContext, goToItem } = useManagedArrayItems()
watch(currentInfoDetail, async model => {
  if (model.id || model.schemaName) {
    await loadInfoDetail(model).then(data => {
      currentComponentModel.value = data.resultData
      componentSchemas.value = data.addons?.components || []
    })
    const projectId = currentComponentModel.value.projectId
    console.log('=============================data', currentComponentModel.value, projectItemMap, projectId)
    if (!projectItemMap[projectId]) {
      loadDetailById(projectId)
        .then((data) => {
          projectItemMap[projectId] = data
        })
    }
  } else {
    currentComponentModel.value = currentInfoDetail.value
  }
  clearItems()
  pushItem(currentComponentModel.value)
}, { immediate: true })
const projectItem = computed(() => projectItemMap[currentComponentModel.value?.projectId])
const deletable = computed(() => inProjectCheckAccess(projectItem.value, AUTHORITY_TYPE.DELETABLE))
const writable = computed(() => inProjectCheckAccess(projectItem.value, AUTHORITY_TYPE.WRITABLE) || deletable.value)
const projectInfos = computed(() => {
  if (projectItem.value?.infoList?.length) {
    return projectItem.value.infoList.map(info => {
      if (info.folderId && projectItem.value.folders?.length) {
        info.folderName = projectItem.value.folders.find(item => item.id === info.folderId)?.folderName
      }
      return info
    })
  }
  return []
})
const componentEditOptions = computed(() => {
  return [{
    showLabel: false,
    prop: 'schemaName',
    placeholder: $i18nKey('common.msg.commonInput', 'api.label.modelName'),
    required: !currentComponentModel.value?.docId,
    attrs: {
      style: {
        minWidth: '350px'
      }
    }
  }, {
    labelKey: 'api.label.apiDocLock',
    type: 'switch',
    prop: 'locked',
    tooltip: $i18nBundle('api.msg.apiDocLocked'),
    enabled: !currentComponentModel.value?.docId
  }, {
    enabled: projectInfos.value?.length > 1,
    showLabel: false,
    prop: 'infoId',
    placeholder: $i18nKey('common.label.commonSelect', 'api.label.importFolder'),
    required: true,
    type: 'select',
    children: projectInfos.value?.map(info => ({
      label: info.folderName,
      value: info.id,
      slots: {
        default: () => <>
          {info.folderName}
          <ElText type="info" class="margin-left1">
            ({$i18nBundle('api.label.importFolder')})
          </ElText>
        </>
      }
    })),
    attrs: {
      clearable: false,
      style: {
        minWidth: '100px'
      }
    }
  }]
})
const descriptionEditOption = computed(() => {
  return {
    showLabel: false,
    placeholder: $i18nKey('common.msg.commonInput', 'common.label.description'),
    prop: 'description',
    attrs: {
      type: 'textarea'
    }
  }
})

const emit = defineEmits(['saveComponent', 'deleteComponent'])
const saveComponent = (form) => {
  form.validate((valid) => {
    if (valid) {
      currentComponentModel.value.schemaContent = contentRef.value
      const data = currentComponentModel.value
      if (data.schemaContent) {
        try {
          JSON.parse(data.schemaContent)
        } catch (e) {
          $coreError($i18nBundle('common.msg.jsonError'))
          return
        }
        ApiProjectInfoDetailApi.saveOrUpdate(data)
          .then((data) => {
            if (data.success) {
              ElMessage.success($i18nBundle('common.msg.saveSuccess'))
              emit('saveComponent', data.resultData)
            }
          })
      } else {
        $coreError($i18nBundle('common.msg.nonNull', ['JSON Schema']))
      }
    }
  })
}
const deleteComponent = () => {
  $coreConfirm($i18nBundle('common.msg.deleteConfirm')).then(() => {
    ApiProjectInfoDetailApi.deleteById(currentComponentModel.value.id)
      .then(() => {
        emit('deleteComponent', currentComponentModel.value)
      })
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

defineExpose({
  saveComponent,
  deleteComponent
})
</script>

<template>
  <el-container class="flex-column height100">
    <common-form
      v-if="currentComponentModel&&schemaContentObj"
      class="padding-left2 padding-right2"
      :model="currentComponentModel"
      inline
      :options="componentEditOptions"
      :submit-label="$t('common.label.save')"
      :show-submit="writable&&!inWindow"
      @submit-form="saveComponent"
    >
      <template #buttons>
        <el-button
          v-if="deletable&&!inWindow"
          type="danger"
          @click="deleteComponent"
        >
          {{ $t('common.label.delete') }}
        </el-button>
      </template>
      <template #after-buttons>
        <common-form-control
          class="form-edit-width-90"
          :model="currentComponentModel"
          :option="descriptionEditOption"
        />
        <el-container
          v-if="managedItems?.length>1"
          class="margin-bottom2"
        >
          <template
            v-for="(item, index) in managedItems"
            :key="index"
          >
            <el-link
              v-if="index<managedItems.length-1"
              type="primary"
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
        </el-container>
        <el-tabs>
          <el-tab-pane
            :label="$t('api.label.dataModel')"
            lazy
          >
            <el-container
              class="flex-column"
            >
              <api-component-schema-edit-tree
                v-if="currentComponentModel&&schemaContentObj&&currentProjectInfo"
                v-model="schemaContentObj"
                :current-info-detail="currentComponentModel"
                :root-name="getSchemaNameLabel(currentComponentModel)"
                :spec-version="currentProjectInfo.specVersion"
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
      </template>
    </common-form>
  </el-container>
</template>

<style scoped>

</style>
