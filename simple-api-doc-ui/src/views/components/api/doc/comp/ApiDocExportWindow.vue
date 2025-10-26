<script setup>
import { checkExportDownloadDocs, downloadExportShareDocs } from '@/api/SimpleShareApi'
import { checkExportProjectDocs, downloadExportProjectDocs } from '@/api/ApiProjectApi'
import { isNumber } from 'lodash-es'
import { calcNodeLeaf } from '@/services/api/ApiFolderService'
import TreeConfigWindow from '@/views/components/utils/TreeConfigWindow.vue'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import { $i18nBundle, $i18nKey } from '@/messages'
import { $coreAlert, $coreConfirm } from '@/utils'
import { computed } from 'vue'

const props = defineProps({
  shareDoc: {
    type: Object,
    default: undefined
  },
  projectItem: {
    type: Object,
    default: undefined
  },
  exportType: {
    type: String,
    default: 'json'
  },
  treeNodes: {
    type: Array,
    default: () => []
  },
  exportAllFunc: {
    type: Function,
    default: null
  }
})

const showTreeConfigWindow = defineModel({
  type: Boolean,
  default: false
})

const treeSelectKeys = defineModel('treeSelectKeys', {
  type: Array,
  default: () => []
})

const exportDocIds = computed(() => {
  return treeSelectKeys.value.filter(id => isNumber(id)) || []
})

const exportSelectedDocs = () => {
  const docIds = exportDocIds.value
  if (docIds.length) {
    $coreConfirm($i18nBundle('api.msg.exportConfirm')).then(() => {
      const param = {
        shareId: props.shareDoc?.shareId,
        projectCode: props.projectItem?.projectCode,
        type: props.exportType
      }
      const isShareDoc = !!props.shareDoc?.shareId
      const checkDownloadFunc = isShareDoc ? checkExportDownloadDocs : checkExportProjectDocs
      const downloadExportFunc = isShareDoc ? downloadExportShareDocs : downloadExportProjectDocs
      checkDownloadFunc({
        ...param,
        docIds
      }).then(resData => {
        if (resData.success && resData.resultData) {
          downloadExportFunc({
            ...param, uuid: resData.resultData
          })
        }
      })
    })
  } else if (props.exportAllFunc) {
    props.exportAllFunc()
  } else {
    $coreAlert($i18nBundle('api.msg.noApiSelected'))
  }
}

</script>

<template>
  <tree-config-window
    v-model="showTreeConfigWindow"
    v-model:selected-keys="treeSelectKeys"
    node-key="treeId"
    :tree-nodes="treeNodes"
    :title="$i18nKey('common.label.commonExport', `common.label.${exportType}`)"
    :ok-label="$t((exportDocIds?.length||!exportAllFunc)?'api.label.exportSelected':'api.label.exportAll')"
    @submit-keys="exportSelectedDocs"
  >
    <template #default="{node, data}">
      <tree-icon-label
        :show-icon="shareDoc?.showTreeIcon!==false"
        :node="node"
        :icon-leaf="calcNodeLeaf(data)"
      >
        <api-method-tag
          v-if="data.docType==='api'"
          :method="data.method"
        />
        {{ node.label }}
      </tree-icon-label>
    </template>
  </tree-config-window>
</template>

<style scoped>

</style>
