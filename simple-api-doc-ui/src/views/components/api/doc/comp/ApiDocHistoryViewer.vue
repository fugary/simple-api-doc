<script setup lang="jsx">
import { ref, computed } from 'vue'
import { loadHistoryDiff, loadHistoryList, recoverFromHistory } from '@/api/ApiDocApi'
import { useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useDefaultPage } from '@/config'
import { ElText, ElTag } from 'element-plus'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import CommonIcon from '@/components/common-icon/index.vue'
import ApiNewHistoryDiffViewer from '@/views/components/api/doc/comp/ApiNewHistoryDiffViewer.vue'
import { $i18nBundle } from '@/messages'
import { $copyText, $coreConfirm } from '@/utils'
import { getDocHistoryViewOptions } from '@/services/api/ApiDocPreviewService'
import { defineTableColumns } from '@/components/utils'

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
const emit = defineEmits(['updateHistory'])
const searchHistories = async (...args) => {
  const data = await searchMethod(...args)
  if (data.success) {
    showHistoryWindow.value = true
    currentDoc.value = data.addons?.current
  }
}
const showHistoryList = (docId) => {
  searchParam.value.queryId = docId
  return searchHistories(1)
}
const limit = 300
/**
 *
 * @type {[CommonTableColumn]}
 */
const columns = computed(() => {
  const isApi = currentDoc.value?.docType === 'api'
  return defineTableColumns([{
    labelKey: isApi ? 'api.label.requestName' : 'api.label.docName',
    formatter (data) {
      return <ElText v-common-tooltip={data.docName}
                     onClick={() => $copyText(data.docName)}
                     style="white-space: nowrap;cursor: pointer;">
        {data.docName}
      </ElText>
    },
    attrs: {
      style: 'white-space: nowrap;'
    }
  }, {
    labelKey: isApi ? 'api.label.apiDescription' : 'api.label.docContent',
    property: 'docContent',
    formatter (data) {
      const docContent = data.docContent || data.description
      let tooltip = docContent
      if (docContent?.length && docContent.length > limit) {
        tooltip = docContent?.substring(0, limit) + '...'
      }
      return <ElText v-common-tooltip={tooltip}
                     onClick={() => $copyText(docContent)}
                     style="white-space: nowrap;cursor: pointer;">
        {docContent}
      </ElText>
    }
  }, {
    labelKey: 'common.label.status',
    formatter (data) {
      let lockStatus = <></>
      if (data.locked) {
        lockStatus = <CommonIcon icon="LockFilled" size={18} class="margin-left1"
                                 style="vertical-align: middle;"
                                 v-common-tooltip={$i18nBundle('api.msg.apiDocLocked')}/>
      }
      return <>
        <DelFlagTag v-model={data.status}/>
        {lockStatus}
      </>
    },
    attrs: {
      align: 'center'
    }
  }, {
    labelKey: 'common.label.version',
    formatter (data) {
      const currentFlag = data.isCurrent ? <ElTag type="success" round={true}>{$i18nBundle('api.label.current')}</ElTag> : ''
      return <>
        <span class="margin-right2">{data.version}</span>
        {currentFlag}
      </>
    }
  }, {
    labelKey: 'common.label.modifier',
    formatter (data) {
      return <ElText>{data.modifier || data.creator}</ElText>
    },
    attrs: {
      align: 'center'
    }
  }, {
    labelKey: 'common.label.modifyDate',
    property: 'createDate',
    dateFormat: 'YYYY-MM-DD HH:mm:ss'
  }])
})
const buttons = computed(() => {
  return [{
    labelKey: 'api.label.compare',
    type: 'primary',
    buttonIf: item => !item.isCurrent,
    click: item => showApiDocDiff(item)
  }, {
    labelKey: 'api.label.recover',
    type: 'warning',
    buttonIf: item => !item.isCurrent,
    click: item => $coreConfirm($i18nBundle('api.msg.recoverFromHistory'))
      .then(() => recoverFromHistory({ queryId: item.id }))
      .then(() => searchHistories())
      .then(() => {
        showHistoryWindow.value = false
        emit('updateHistory', currentDoc.value)
      })
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
    modifiedDoc.value = {
      ...currentDoc.value,
      isCurrent: true
    }
    showDiffViewer.value = true
  } else {
    const queryId = item.isCurrent ? item.id : item.modifyFrom
    loadHistoryDiff({
      queryId,
      version: item.version
    }).then(data => {
      if (data.success) {
        if (item.isCurrent) {
          originalDoc.value = data.resultData?.modifiedDoc
          modifiedDoc.value = {
            ...currentDoc.value,
            isCurrent: true
          }
        } else {
          originalDoc.value = data.resultData?.originalDoc || {}
          const modDoc = data.resultData?.modifiedDoc
          modifiedDoc.value = {
            ...modDoc,
            modifyDate: modDoc?.createDate,
            modifier: modDoc?.creator
          }
        }
      }
      showDiffViewer.value = true
    })
  }
}

const calcTableData = computed(() => {
  if (currentDoc.value) {
    const currentHistory = {
      ...currentDoc.value,
      createDate: currentDoc.value.modifyDate || currentDoc.value.createDate,
      creator: currentDoc.value.modifier || currentDoc.value.creator,
      isCurrent: true
    }
    return [currentHistory, ...tableData.value]
  }
  return tableData.value
})

defineExpose({
  showHistoryList
})
</script>

<template>
  <common-window
    v-model="showHistoryWindow"
    :title="$t('api.label.historyVersions')"
    width="1100px"
    show-fullscreen
    :show-cancel="false"
    :ok-label="$t('common.label.close')"
    class="flex-column"
  >
    <common-table
      v-model:page="searchParam.page"
      :data="calcTableData"
      :columns="columns"
      :buttons="buttons"
      buttons-slot="buttons"
      :buttons-column-attrs="{width:'220px'}"
      :loading="loading"
      @page-size-change="searchHistories()"
      @current-page-change="searchHistories()"
    />
    <api-new-history-diff-viewer
      v-model="showDiffViewer"
      :original-doc="originalDoc"
      :modified-doc="modifiedDoc"
      :history-options-method="getDocHistoryViewOptions"
    />
  </common-window>
</template>

<style scoped>

</style>
