<script setup lang="jsx">
import { $i18nBundle, $i18nKey } from '@/messages'
import { computed, ref, watch } from 'vue'
import { useMonacoEditorOptions } from '@/vendors/monaco-editor'
import UrlCopyLink from '@/views/components/api/UrlCopyLink.vue'
import { ElText } from 'element-plus'
import { $coreConfirm } from '@/utils'
import ApiComponentSchemaEditTree from '@/views/components/api/project/schema/ApiComponentSchemaEditTree.vue'

const props = defineProps({
  projectInfos: {
    type: Array,
    default: () => []
  },
  componentSchemas: {
    type: Array,
    default: () => ([])
  },
  writable: {
    type: Boolean,
    default: false
  },
  deletable: {
    type: Boolean,
    default: false
  }
})

const currentComponentModel = defineModel({
  type: Object,
  required: true
})
const componentEditOptions = computed(() => {
  return [{
    showLabel: false,
    prop: 'schemaName',
    placeholder: $i18nKey('common.msg.commonInput', 'api.label.modelName'),
    required: true,
    attrs: {
      style: {
        minWidth: '350px'
      }
    }
  }, {
    labelKey: 'api.label.apiDocLock',
    type: 'switch',
    prop: 'contentType',
    tooltip: $i18nBundle('api.msg.apiDocLocked'),
    attrs: {
      activeValue: 'manual',
      inactiveValue: 'auto'
    }
  }, {
    enabled: props.projectInfos?.length > 1,
    showLabel: false,
    prop: 'infoId',
    placeholder: $i18nKey('common.msg.commonSelect', 'api.label.importFolder'),
    required: true,
    type: 'select',
    children: props.projectInfos?.map(info => ({
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
      emit('saveComponent', currentComponentModel.value)
    }
  })
}
const deleteComponent = () => {
  $coreConfirm($i18nBundle('common.msg.deleteConfirm')).then(() => {
    emit('deleteComponent', currentComponentModel.value)
  })
}
const { contentRef, languageRef, editorRef, monacoEditorOptions, languageModel, languageSelectOption, formatDocument } = useMonacoEditorOptions({ readOnly: false })
languageRef.value = 'json'
const schemaContentObj = ref({})
watch(() => currentComponentModel.value?.schemaContent, (schemaContent) => {
  contentRef.value = schemaContent
  try {
    schemaContentObj.value = JSON.parse(schemaContent || '{}')
  } catch (e) { // 忽略错误保证能正常显示
    console.error(e)
  }
}, { immediate: true })
watch(schemaContentObj, () => {
  if (currentComponentModel.value) {
    currentComponentModel.value.schemaContent = JSON.stringify(schemaContentObj.value)
  }
}, { deep: true })
const codeHeight = '400px'

const currentProjectInfo = computed(() => {
  return props.projectInfos.find(info => info.id === currentComponentModel.value.infoId)
})
</script>

<template>
  <el-container class="flex-column height100">
    <common-form
      class="padding-left2 padding-right2"
      :model="currentComponentModel"
      inline
      :options="componentEditOptions"
      :submit-label="$t('common.label.save')"
      :show-submit="writable"
      @submit-form="saveComponent"
    >
      <template #buttons>
        <el-button
          v-if="deletable"
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
        <el-tabs>
          <el-tab-pane
            :label="$t('api.label.dataModel')"
            lazy
          >
            <el-container
              class="flex-column"
            >
              <api-component-schema-edit-tree
                v-model="schemaContentObj"
                :current-info-detail="currentComponentModel"
                :root-name="currentComponentModel.schemaName"
                :spec-version="currentProjectInfo.specVersion"
                :component-schemas="componentSchemas"
                :show-merge-all-of="false"
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
