<script setup lang="jsx">
import { computed, ref, useTemplateRef } from 'vue'
import { $coreAlert, $coreError, getStyleGrow } from '@/utils'
import { defineFormOptions } from '@/components/utils'
import {
  IMPORT_AUTH_TYPES,
  IMPORT_DUPLICATE_STRATEGY,
  IMPORT_SOURCE_TYPES,
  IMPORT_TYPES
  , AUTH_TYPE
} from '@/consts/ApiConstants'
import { ElButton, ElUpload, ElText } from 'element-plus'
import {
  calcProjectIconUrl,
  importProject, uploadFiles
} from '@/api/ApiProjectApi'
import { $i18nBundle, $i18nKey } from '@/messages'
import { AUTH_OPTION_CONFIG } from '@/services/api/ApiAuthorizationService'
import { isFunction } from 'lodash-es'
import { useFolderTreeNodes } from '@/services/api/ApiFolderService'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import CommonIcon from '@/components/common-icon/index.vue'
import { addOrEditFolderWindow } from '@/utils/DynamicUtils'

const props = defineProps({
  project: {
    type: Object,
    default: null
  },
  showButtons: {
    type: Boolean,
    default: false
  },
  defaultGroupCode: {
    type: String,
    default: ''
  },
  groupOptions: {
    type: Array,
    default: () => []
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
      prop: `authContentModel.${option.prop}`,
      style: getStyleGrow(5)
    }
  })
})
const { folderTreeNodes, folders, loadValidFolders, getToFolder } = useFolderTreeNodes()
loadValidFolders(props.project?.id).then(() => {
  importModel.value.toFolder = getToFolder(props.project?.infoList?.[0]?.folderId)
})
const importFolders = computed(() => props.project?.infoList?.map(info => info.folderId) || [])

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
    labelKey: 'api.label.projectIcon',
    prop: 'iconUrl',
    enabled: !existsProj,
    tooltip: $i18nBundle('api.msg.projectIconTooltip'),
    slots: {
      prefix () {
        if (importModel.value.iconUrl) {
          const iconUrl = calcProjectIconUrl(importModel.value?.iconUrl)
          return <img src={iconUrl} class="api-project-icon-cls margin-right1" alt="logo"/>
        }
      },
      append () {
        const changeFile = ($event) => {
          uploadFiles($event.raw, (resultData) => {
            importModel.value.iconUrl = resultData?.[0]
          })
        }
        return <ElUpload class="custom-img-upload" showFileList={false} autoUpload={false} onChange={(...args) => changeFile(...args)} accept="image/*">
          <CommonIcon size={18} icon="Picture" class="append-icon-cls"/>
        </ElUpload>
      }
    }
  }, {
    enabled: urlMode,
    required: true,
    labelKey: 'api.label.importUrl',
    prop: 'url',
    rules: [{
      message: $i18nBundle('api.msg.proxyUrlMsg'),
      validator: () => {
        return !importModel.value.url || /^https?:\/\/.+/.test(importModel.value.url.trim())
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
          <ElButton type="primary">
            <CommonIcon size={18} icon="FileUploadFilled"/>
            {$i18nBundle('api.label.selectFile')}
          </ElButton>
          <span style="display: inline-block; margin-left: 10px;">{importFiles.value?.[0]?.name}</span>
        </>
      },
      tip: () => <div class="el-upload__tip">{$i18nBundle('api.msg.importFileLimit')}</div>
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
      default: ({ node }) => <TreeIconLabel node={node} iconLeaf="Folder">
        {node.label} {importFolders.value?.includes(node.data?.id) ? <ElText type="success">({$i18nBundle('api.label.importFolder')})</ElText> : ''}
        {node.data?.rootFlag ? <CommonIcon icon="Refresh" v-common-tooltip={$i18nBundle('common.label.refresh')} size={14} class="pointer margin-left2" onClick={() => loadValidFolders(importModel.value?.projectId)}/> : ''}
      </TreeIconLabel>
    },
    tooltip: $i18nKey('common.label.commonAdd', 'api.label.folder'),
    tooltipIcon: 'CirclePlusFilled',
    tooltipLinkAttrs: {
      type: 'primary'
    },
    tooltipFunc (event) {
      const parentFolder = folders.value.find(folder => importModel.value?.toFolder === folder.id)
      addOrEditFolderWindow(null, props.project?.id, parentFolder, {
        onSavedFolder: (data) => {
          loadValidFolders(props.project?.id)
            .then(() => (importModel.value.toFolder = data.id))
        }
      })
      event.preventDefault()
    }
  }, {
    enabled: !props.project?.id && !!props.groupOptions.length,
    labelKey: 'api.label.projectGroups1',
    prop: 'groupCode',
    value: props.defaultGroupCode,
    type: 'select',
    children: props.groupOptions
  }]).map(option => {
    const style = { ...getStyleGrow(10), ...option.style || {} }
    return {
      ...option, style
    }
  })
})
const emit = defineEmits(['import-success'])
const importFormRef = useTemplateRef('importForm')
const doImportProject = (autoAlert = true) => {
  importFormRef.value?.form.validate((valid) => {
    if (valid) {
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
    }
  })
}

defineExpose({
  doImportProject
})

</script>

<template>
  <el-container class="flex-column">
    <common-form
      ref="importForm"
      label-width="130px"
      class="form-edit-width-90"
      :options="formOptions"
      :show-buttons="showButtons"
      :model="importModel"
      :submit-label="$t('api.label.importData')"
      class-name="common-form-auto"
      v-bind="$attrs"
      @submit-form="doImportProject()"
    />
  </el-container>
</template>

<style scoped>

</style>
