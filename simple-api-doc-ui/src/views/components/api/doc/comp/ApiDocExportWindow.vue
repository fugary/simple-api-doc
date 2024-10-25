<script setup>
import { checkExportDownloadDocs, downloadExportShareDocs } from '@/api/SimpleShareApi'
import { isNumber } from 'lodash-es'
import { calcNodeLeaf } from '@/services/api/ApiFolderService'
import TreeConfigWindow from '@/views/components/utils/TreeConfigWindow.vue'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import { $i18nBundle, $i18nConcat } from '@/messages'
import { $coreAlert, $coreConfirm } from '@/utils'

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

const toExportApiDocs = () => {
  showTreeConfigWindow.value = true
}

const exportSelectedDocs = (data) => {
  const docIds = data?.filter(id => isNumber(id))
  if (docIds.length) {
    $coreConfirm($i18nBundle('api.msg.exportConfirm')).then(() => {
      const param = {
        shareId: props.shareDoc?.shareId,
        type: props.exportType
      }
      checkExportDownloadDocs({
        ...param,
        docIds
      }).then(resData => {
        if (resData.success && resData.resultData) {
          downloadExportShareDocs({
            ...param, uuid: resData.resultData
          })
        }
        console.log('=============================data', resData)
      })
    })
  } else {
    $coreAlert($i18nBundle('api.msg.noApiSelected'))
  }
}

defineExpose({
  toExportApiDocs
})

</script>

<template>
  <tree-config-window
    v-model="showTreeConfigWindow"
    v-model:selected-keys="treeSelectKeys"
    node-key="treeId"
    :tree-nodes="treeNodes"
    height="400px"
    :title="$i18nConcat($t('api.label.exportSelectedApi'), exportType?.toUpperCase())"
    @submit-keys="exportSelectedDocs"
  >
    <template #default="{node, data}">
      <tree-icon-label
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
