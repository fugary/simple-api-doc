<script setup lang="jsx">
import { computed, ref } from 'vue'
import { $coreAlert, $coreError } from '@/utils'
import { defineFormOptions } from '@/components/utils'
import {
  IMPORT_AUTH_TYPES,
  IMPORT_DUPLICATE_STRATEGY,
  IMPORT_SOURCE_TYPES,
  IMPORT_TYPES
  , AUTH_TYPE
} from '@/consts/ApiConstants'
import { ElButton } from 'element-plus'
import {
  importProject
} from '@/api/ApiProjectApi'
import { $i18nBundle } from '@/messages'
import { AUTH_OPTION_CONFIG } from '@/services/api/ApiAuthorizationService'
import { isFunction } from 'lodash-es'
import { useFolderTreeNodes } from '@/services/api/ApiFolderService'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'

const props = defineProps({
  project: {
    type: Object,
    default: null
  },
  showButtons: {
    type: Boolean,
    default: false
  }
})

const importModel = ref({
  importType: IMPORT_TYPES[0].value,
  authType: AUTH_TYPE.NONE,
  sourceType: IMPORT_SOURCE_TYPES[0].value,
  overwriteMode: IMPORT_DUPLICATE_STRATEGY[0].value,
  authContentModel: {}
})
const authOptions = computed(() => {
  let options = []
  if (importModel.value.importType === 'url') {
    options = AUTH_OPTION_CONFIG[importModel.value.authType]?.options || []
    if (isFunction(options)) {
      options = options()
    }
  }
  return options.map(option => {
    return {
      ...option,
      prop: `authContentModel.${option.prop}`
    }
  })
})
const { folderTreeNodes, loadValidFolders } = useFolderTreeNodes()
loadValidFolders(props.project?.id).then(() => {
  importModel.value.toFolder = folderTreeNodes.value[0]?.id
})

const importFiles = ref([])
const formOptions = computed(() => {
  const existsProj = !!props.project
  const urlMode = importModel.value.importType === 'url'
  return defineFormOptions([{
    labelKey: 'api.label.importType',
    prop: 'importType',
    type: 'segmented',
    attrs: {
      clearable: false,
      options: IMPORT_TYPES.map(item => ({
        value: item.value,
        label: $i18nBundle(item.labelKey)
      }))
    }
  }, {
    enabled: false,
    labelKey: 'api.label.duplicateStrategy',
    prop: 'overwriteMode',
    type: 'select',
    children: IMPORT_DUPLICATE_STRATEGY,
    tooltip: $i18nBundle('api.msg.duplicateStrategy'),
    attrs: {
      clearable: false
    }
  }, {
    labelKey: 'api.label.source',
    prop: 'sourceType',
    type: 'radio-group',
    children: IMPORT_SOURCE_TYPES
  }, {
    labelKey: 'api.label.projectName',
    prop: 'projectName',
    required: true,
    enabled: !existsProj
  }, {
    enabled: urlMode,
    required: true,
    labelKey: 'api.label.importUrl',
    prop: 'url',
    rules: [{
      message: $i18nBundle('api.msg.proxyUrlMsg'),
      validator: () => {
        return !importModel.value.url || /^https?:\/\//.test(importModel.value.url.trim())
      }
    }]
  }, {
    enabled: urlMode,
    labelKey: 'api.label.authType',
    prop: 'authType',
    type: 'radio-group',
    children: IMPORT_AUTH_TYPES
  }, ...authOptions.value, {
    enabled: !urlMode,
    labelKey: 'api.label.importFile',
    type: 'upload',
    attrs: {
      fileList: importFiles.value,
      'onUpdate:fileList': (files) => {
        importFiles.value = files
      },
      limit: 1,
      showFileList: false,
      autoUpload: false,
      onExceed (files) {
        importFiles.value = [...files.map(file => ({
          name: file.name,
          status: 'ready',
          size: file.size,
          raw: file
        }))] // 文件覆盖
      }
    },
    slots: {
      trigger () {
        return <>
          <ElButton type="primary">{$i18nBundle('api.label.selectFile')}</ElButton>
          <span style="display: inline-block; margin-left: 10px;">{importFiles.value?.[0]?.name}</span>
        </>
      },
      tip: () => <div className="el-upload__tip">{$i18nBundle('api.msg.importFileLimit')}</div>
    }
  }, {
    enabled: !!props.project?.id,
    labelKey: 'api.label.targetFolder',
    type: 'tree-select',
    prop: 'toFolder',
    attrs: {
      checkStrictly: true,
      filterable: true,
      nodeKey: 'id',
      data: folderTreeNodes.value,
      clearable: false,
      defaultExpandedKeys: folderTreeNodes.value[0]?.id ? [folderTreeNodes.value[0]?.id] : []
    },
    slots: {
      default: ({ node }) => <TreeIconLabel node={node} iconLeaf="Folder"/>
    }
  }])
})
const emit = defineEmits(['import-success'])
const doImportProject = (autoAlert = true) => {
  if (importFiles.value?.length || importModel.value.importType === 'url') {
    importModel.value.projectId = props.project?.id
    const modelParam = { ...importModel.value }
    if (importModel.value.authType !== AUTH_TYPE.NONE) {
      modelParam.authContent = JSON.stringify(modelParam.authContentModel)
      delete modelParam.authContentModel
    }
    importProject(importFiles.value, modelParam, {
      loading: true
    }).then(data => {
      if (data.success) {
        if (autoAlert) {
          $coreAlert($i18nBundle('api.msg.importFileSuccess', [data.resultData?.projectName]))
        }
        emit('import-success', data.resultData)
      }
    })
  } else {
    $coreError($i18nBundle('api.msg.importFileNoFile'))
  }
  return false
}

defineExpose({
  doImportProject
})

</script>

<template>
  <el-container class="flex-column">
    <common-form
      label-width="130px"
      class="form-edit-width-90"
      :options="formOptions"
      :show-buttons="showButtons"
      :model="importModel"
      :submit-label="$t('api.label.importData')"
      v-bind="$attrs"
      @submit-form="doImportProject()"
    />
  </el-container>
</template>

<style scoped>

</style>
