<script setup lang="jsx">
import { ref, computed } from 'vue'
import ApiDocApi, { loadHistoryDiff, loadHistoryList } from '@/api/ApiDocApi'
import { useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useDefaultPage } from '@/config'
import { ElText } from 'element-plus'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import ApiDocHistoryDiffViewer from '@/views/components/api/doc/comp/ApiDocHistoryDiffViewer.vue'

const showHistoryWindow = defineModel({
  type: Boolean,
  default: false
})

const currentDoc = ref()
const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { page: useDefaultPage() },
  searchMethod: loadHistoryList,
  saveParam: false
})
const showHistoryList = async (docId) => {
  const data = await ApiDocApi.getById(docId)
  if (data.success) {
    currentDoc.value = data.resultData
    showHistoryWindow.value = true
    searchParam.value.docId = docId
    searchMethod(1)
  }
}
const limit = 300
/**
 *
 * @type {[CommonTableColumn]}
 */
const columns = [{
  labelKey: 'api.label.docName',
  formatter (data) {
    return <ElText v-common-tooltip={data.docName} style="white-space: nowrap;">
      {data.docName}
    </ElText>
  },
  attrs: {
    style: 'white-space: nowrap;'
  }
}, {
  labelKey: 'api.label.docContent',
  property: 'docContent',
  formatter (data) {
    return <ElText v-common-tooltip={data.docContent?.substring(0, limit) + '...'} style="white-space: nowrap;">
      {data.docContent}
    </ElText>
  }
}, {
  labelKey: 'common.label.status',
  formatter (data) {
    return <DelFlagTag v-model={data.status}/>
  },
  attrs: {
    align: 'center'
  }
}, {
  labelKey: 'common.label.version',
  property: 'docVersion'
},
{
  labelKey: 'common.label.modifyDate',
  property: 'createDate',
  dateFormat: 'YYYY-MM-DD HH:mm:ss'
}]
const buttons = computed(() => {
  return [{
    labelKey: 'api.label.compare',
    type: 'primary',
    click: item => showApiDocDiff(item)
  }, {
    labelKey: 'api.label.viewDiff',
    type: 'success',
    click: item => showApiDocDiff(item, true)
  }]
})

const showDiffViewer = ref(false)
const originalDoc = ref()
const modifiedDoc = ref()
const showApiDocDiff = (item, diff) => {
  if (!diff) {
    originalDoc.value = item
    modifiedDoc.value = currentDoc.value
    showDiffViewer.value = true
  } else {
    loadHistoryDiff({
      docId: item.docId,
      docVersion: item.docVersion
    }).then(data => {
      originalDoc.value = data.resultData?.originalDoc || {}
      const modDoc = data.resultData?.modifiedDoc
      modifiedDoc.value = {
        ...modDoc,
        modifyDate: modDoc?.createDate,
        modifier: modDoc?.creator
      }
      showDiffViewer.value = true
    })
  }
}

defineExpose({
  showHistoryList
})
</script>

<template>
  <common-window
    v-model="showHistoryWindow"
    :title="$t('api.label.historyVersions')"
    width="1000px"
    show-fullscreen
    :show-cancel="false"
    :ok-label="$t('common.label.close')"
    class="flex-column"
  >
    <common-table
      v-model:page="searchParam.page"
      :data="tableData"
      :columns="columns"
      :buttons="buttons"
      buttons-slot="buttons"
      :buttons-column-attrs="{width:'200px'}"
      :loading="loading"
      @page-size-change="searchMethod()"
      @current-page-change="searchMethod()"
    />
    <api-doc-history-diff-viewer
      v-model="showDiffViewer"
      :original-doc="originalDoc"
      :modified-doc="modifiedDoc"
    />
  </common-window>
</template>

<style scoped>

</style>
