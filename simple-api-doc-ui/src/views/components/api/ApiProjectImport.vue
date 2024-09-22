<script setup lang="jsx">
import { computed, ref } from 'vue'
import { $coreAlert, $coreError } from '@/utils'
import { defineFormOptions } from '@/components/utils'
import {
  IMPORT_AUTH_TYPES,
  IMPORT_DUPLICATE_STRATEGY,
  IMPORT_SOURCE_TYPES,
  IMPORT_TYPES
} from '@/consts/ApiConstants'
import { ElButton } from 'element-plus'
import {
  uploadFiles
} from '@/api/ApiProjectApi'
import { $i18nBundle } from '@/messages'
import { AUTH_OPTION_CONFIG } from '@/services/mock/ApiAuthorizationService'
import { isFunction } from 'lodash-es'

const props = defineProps({
  projectCode: {
    type: String,
    default: ''
  },
  projectOptions: {
    type: Array,
    default: () => []
  }
})
const showWindow = defineModel('modelValue', { type: Boolean, default: false })
const importModel = ref({
  type: 'swagger',
  authType: 'none',
  importSourceType: IMPORT_SOURCE_TYPES[0].value,
  duplicateStrategy: IMPORT_DUPLICATE_STRATEGY[0].value
})
const authOptions = computed(() => {
  let options = []
  if (importModel.value.importSourceType === 'url') {
    options = AUTH_OPTION_CONFIG[importModel.value.authType]?.options || []
    if (isFunction(options)) {
      options = options()
    }
  }
  return options
})
const importFiles = ref([])
const formOptions = computed(() => {
  const urlMode = importModel.value.importSourceType === 'url'
  return defineFormOptions([{
    labelKey: 'api.label.project',
    prop: 'projectCode',
    type: 'select',
    enabled: props.projectOptions.length > 1,
    children: props.projectOptions,
    attrs: {
      clearable: false
    }
  }, {
    labelKey: 'api.label.source',
    prop: 'type',
    type: 'select',
    children: IMPORT_TYPES,
    attrs: {
      clearable: false
    }
  }, {
    labelKey: 'api.label.duplicateStrategy',
    prop: 'duplicateStrategy',
    type: 'select',
    children: IMPORT_DUPLICATE_STRATEGY,
    tooltip: $i18nBundle('api.msg.duplicateStrategy'),
    attrs: {
      clearable: false
    }
  }, {
    labelKey: 'api.label.importSourceType',
    prop: 'importSourceType',
    type: 'radio-group',
    children: IMPORT_SOURCE_TYPES
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
const doImportGroups = () => {
  if (importFiles.value?.length) {
    uploadFiles(importFiles.value, importModel.value, {
      loading: true
    }).then(data => {
      if (data.success) {
        $coreAlert($i18nBundle('api.msg.importFileSuccess', [data.resultData]))
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
    :ok-click="doImportGroups"
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
