<script setup lang="jsx">
import { computed, inject } from 'vue'
import { getFolderPaths } from '@/services/api/ApiProjectService'
import { showHistoryListWindow } from '@/utils/DynamicUtils'
import { defineTableColumns } from '@/components/utils'
import { $copyText } from '@/utils'
import { $i18nBundle } from '@/messages'
import { ElText, ElTag } from 'element-plus'
import CommonIcon from '@/components/common-icon/index.vue'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import { loadHistoryDiff, loadHistoryList, recoverFromHistory } from '@/api/ApiDocApi'
import { showCompareWindowNew } from '@/services/api/ApiDocEditService'
import { getDocHistoryViewOptions } from '@/services/api/ApiDocPreviewService'
const props = defineProps({
  editable: {
    type: Boolean,
    default: false
  },
  historyCount: {
    type: Number,
    default: 0
  },
  currentDocDetail: {
    type: Object,
    default: undefined
  }
})
const currentDoc = defineModel({
  type: Object,
  default: undefined
})
const folderPaths = computed(() => {
  if (currentDoc.value) {
    return getFolderPaths(currentDoc.value)
  }
  return []
})
const docDetailInfo = computed(() => props.currentDocDetail || currentDoc.value)
const emit = defineEmits(['updateHistory'])
const toShowHistoryWindow = (current) => {
  const isApi = current.docType === 'api'
  const limit = 300
  const emptyDoc = { docType: current.docType }
  showHistoryListWindow({
    columns: defineTableColumns([{
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
        const currentFlag = data.current ? <ElTag type="success" round={true}>{$i18nBundle('api.label.current')}</ElTag> : ''
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
    }]),
    searchFunc: param => loadHistoryList({ ...param, queryId: current.id }),
    compareFunc: async (modified, target, previous) => {
      let original = modified
      if (previous) {
        await loadHistoryDiff({
          queryId: modified.id,
          version: modified.version
        }).then(data => {
          modified = data.resultData?.modifiedDoc || emptyDoc
          original = data.resultData?.originalDoc || emptyDoc
          modified.current = !modified.modifyFrom
        })
      } else {
        modified = target
      }
      showCompareWindowNew({
        modified,
        original,
        historyOptionsMethod: getDocHistoryViewOptions
      })
    },
    recoverFunc: props.editable ? recoverFromHistory : null,
    onUpdateHistory: data => emit('updateHistory', data)
  })
}
const showAffixBtn = inject('showAffixBtn', null)
</script>

<template>
  <el-header
    style="min-height: var(--el-header-height);height:auto;"
    :style="showAffixBtn?'padding-left: 50px;':''"
  >
    <el-breadcrumb
      v-if="folderPaths.length>1"
      class="margin-top3"
    >
      <el-breadcrumb-item
        v-for="(folderPath, index) in folderPaths"
        :key="index"
      >
        {{ folderPath }}
      </el-breadcrumb-item>
    </el-breadcrumb>
    <h2 class="margin-bottom2">
      {{ currentDoc?.docName || currentDoc?.url }}
      <el-button
        v-if="editable"
        class="margin-left2"
        type="primary"
        @click="currentDoc.editing=true"
      >
        {{ $t('common.label.edit') }}
      </el-button>
      <el-link
        v-if="historyCount"
        class="margin-left2"
        type="primary"
        @click="toShowHistoryWindow(currentDoc)"
      >
        {{ $t('api.label.historyVersions') }}
        <el-text type="info">
          ({{ historyCount }})
        </el-text>
      </el-link>
      <!-- 添加修改人和修改时间 -->
      <el-row v-if="docDetailInfo&&(docDetailInfo.modifyDate||docDetailInfo.createDate)">
        <el-col>
          <el-text
            type="info"
            class="margin-right3"
          >
            {{ $t('common.label.modifier') }}: {{ docDetailInfo.modifier||docDetailInfo.creator||'import' }}
          </el-text>
          <el-text
            type="info"
          >
            {{ $t('common.label.modifyDate') }}: {{ $date(docDetailInfo.modifyDate||docDetailInfo.createDate, 'YYYY-MM-DD HH:mm') }}
          </el-text>
        </el-col>
      </el-row>
    </h2>
  </el-header>
</template>

<style scoped>

</style>
