<script setup>
import { ref } from 'vue'
import { checkExportDownloadDocs, downloadExportShareDocs } from '@/api/SimpleShareApi'
import { isNumber } from 'lodash-es'
import { calcProjectItem, filterProjectItem } from '@/services/api/ApiProjectService'
import { calcNodeLeaf } from '@/services/api/ApiFolderService'
import TreeConfigWindow from '@/views/components/utils/TreeConfigWindow.vue'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'

const shareDoc = ref()
const projectItem = ref()
const showTreeConfigWindow = ref(false)
const treeConfigNodes = ref([])
const treeSelectKeys = ref([])
const currentExportType = ref('json')

const toExportApiDocs = (projItem, shDoc, type) => {
  projectItem.value = projItem
  shareDoc.value = shDoc
  if (!treeConfigNodes.value?.length) {
    filterTreeNodes()
  }
  currentExportType.value = type
  showTreeConfigWindow.value = true
}

const exportSelectedDocs = (data) => {
  console.log('==========================data', data, currentExportType.value)
  const param = {
    shareId: shareDoc.value?.shareId,
    type: currentExportType.value
  }
  checkExportDownloadDocs({
    ...param,
    docIds: data?.filter(id => isNumber(id))
  }).then(resData => {
    if (resData.success && resData.resultData) {
      downloadExportShareDocs({
        ...param, uuid: resData.resultData
      })
    }
    console.log('=============================data', resData)
  })
}
const filterTreeNodes = (keyword) => {
  const { docTreeNodes } = calcProjectItem(filterProjectItem(projectItem.value, keyword),
    { keyword }, {})
  treeConfigNodes.value = docTreeNodes
}

defineExpose({
  toExportApiDocs
})

</script>

<template>
  <tree-config-window
    v-model="showTreeConfigWindow"
    :selected-keys="treeSelectKeys"
    node-key="treeId"
    :tree-nodes="treeConfigNodes"
    height="400px"
    :title="$t('api.label.exportSelectedApi')"
    @submit-keys="exportSelectedDocs"
    @filter-tree-nodes="filterTreeNodes"
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
