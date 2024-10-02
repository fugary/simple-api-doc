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

const props = defineProps({
  project: {
    type: Object,
    default: null
  }
})
const showWindow = defineModel('modelValue', { type: Boolean, default: false })
const importModel = ref({
  importType: IMPORT_TYPES[0].value,
  authType: AUTH_TYPE.NONE,
  sourceType: IMPORT_SOURCE_TYPES[0].value,
  overwriteMode: IMPORT_DUPLICATE_STRATEGY[0].value
})
const authOptions = computed(() => {
  let options = []
  if (importModel.value.importType === 'url') {
    options = AUTH_OPTION_CONFIG[importModel.value.authType]?.options || []
    if (isFunction(options)) {
      options = options()
    }
  }
  return options
})
const importFiles = ref([])
const formOptions = computed(() => {
  const existsProj = !!props.project
  const urlMode = importModel.value.importType === 'url'
  return defineFormOptions([{
    labelKey: 'api.label.source',
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
    enabled: existsProj,
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
    prop: 'url'
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
  }])
})
const emit = defineEmits(['import-success'])
const doImportProject = () => {
  if (importFiles.value?.length || importModel.value.importType === 'url') {
    importProject(importFiles.value, importModel.value, {
      loading: true
    }).then(data => {
      if (data.success) {
        $coreAlert($i18nBundle('api.msg.importFileSuccess', [data.resultData?.projectName]))
        showWindow.value = false
        emit('import-success', data)
      }
    })
  } else {
    $coreError($i18nBundle('api.msg.importFileNoFile'))
  }
  return false
}

</script>

<template>
  <common-window
    v-model="showWindow"
    :title="$t('api.msg.importFileTitle')"
    append-to-body
    destroy-on-close
    width="800px"
    :ok-click="doImportProject"
  >
    <el-container class="flex-column">
      <common-form
        label-width="130px"
        class="form-edit-width-90"
        :options="formOptions"
        :show-buttons="false"
        :model="importModel"
        v-bind="$attrs"
      />
    </el-container>
  </common-window>
</template>

<style scoped>

</style>
