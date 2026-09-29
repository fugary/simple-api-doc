<script setup lang="jsx">
import { computed, inject } from 'vue'
import { getFolderPaths, getDocRecentInfo } from '@/services/api/ApiProjectService'
import { showHistoryListWindow, showApiCompareWindow } from '@/utils/DynamicUtils'
import { defineTableColumns } from '@/components/utils'
import { $copyText, formatDate } from '@/utils'
import { $i18nBundle } from '@/messages'
import { ElText, ElTag } from 'element-plus'
import CommonIcon from '@/components/common-icon/index.vue'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import { loadHistoryDiff, loadHistoryList, recoverFromHistory } from '@/api/ApiDocApi'
import { getDocHistoryViewOptions } from '@/services/api/ApiDocPreviewService'
import emitter from '@/vendors/emitter'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
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
  },
  preferenceId: {
    type: String,
    default: ''
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

const recentInfo = computed(() => getDocRecentInfo(docDetailInfo.value))

const shareConfigStore = useShareConfigStore()
const pref = computed(() => (props.preferenceId && shareConfigStore.sharePreferenceView[props.preferenceId]) || {})
const canNavBack = computed(() => (pref.value.navIndex ?? -1) > 0)
const canNavForward = computed(() => {
  const index = pref.value.navIndex ?? -1
  const stack = pref.value.navStack || []
  return index >= 0 && index < stack.length - 1
})

const handleNavBack = () => {
  const targetDocId = shareConfigStore.navBack(props.preferenceId)
  if (targetDocId) {
    emitter.emit('select-api-doc', { id: targetDocId })
  }
}

const handleNavForward = () => {
  const targetDocId = shareConfigStore.navForward(props.preferenceId)
  if (targetDocId) {
    emitter.emit('select-api-doc', { id: targetDocId })
  }
}

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
        let deprecatedStatus = <></>
        if (data.deprecated) {
          deprecatedStatus = <ElTag type="warning" size="small" class="margin-left1" round={true}>
            {$i18nBundle('api.label.deprecated')}
          </ElTag>
        }
        return <>
          <DelFlagTag v-model={data.status}/>
          {lockStatus}
          {deprecatedStatus}
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
      formatter (data) {
        return formatDate(data.modifyDate || data.createDate)
      }
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
      showApiCompareWindow({
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
    <div class="doc-header-nav-bar margin-top3">
      <div class="doc-nav-actions">
        <el-button
          link
          size="small"
          class="doc-nav-btn"
          :disabled="!canNavBack"
          :title="$t('api.label.navBack')"
          @click="handleNavBack"
        >
          <common-icon
            icon="ArrowBackFilled"
            :size="18"
          />
        </el-button>
        <el-button
          link
          size="small"
          class="doc-nav-btn"
          :disabled="!canNavForward"
          :title="$t('api.label.navForward')"
          @click="handleNavForward"
        >
          <common-icon
            icon="ArrowForwardFilled"
            :size="18"
          />
        </el-button>
      </div>
      <span
        v-if="folderPaths.length > 0"
        class="doc-header-path-prefix"
      >/</span>
      <el-breadcrumb
        v-if="folderPaths.length > 0"
        class="doc-header-breadcrumb"
      >
        <el-breadcrumb-item
          v-for="(folderPath, index) in folderPaths"
          :key="index"
        >
          {{ folderPath }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <h2 class="margin-bottom1">
      <el-text
        v-if="currentDoc?.deprecated"
        tag="del"
        type="warning"
        size="large"
        style="font-size: inherit; font-weight: inherit;"
      >
        {{ currentDoc?.docName || currentDoc?.url }}
      </el-text>
      <el-text
        v-else-if="currentDoc?.status === 0"
        type="danger"
        size="large"
        style="font-size: inherit; font-weight: inherit;"
      >
        {{ currentDoc?.docName || currentDoc?.url }}
      </el-text>
      <span v-else>{{ currentDoc?.docName || currentDoc?.url }}</span>
      <el-tag
        v-if="recentInfo"
        v-common-tooltip="recentInfo.tooltip"
        type="primary"
        size="small"
        round
        effect="plain"
        class="margin-left2 recent-header-tag"
      >
        <span class="recent-header-dot dot-upd" /> UPD
      </el-tag>
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
.doc-header-nav-bar {
  display: flex;
  align-items: center;
  min-height: 26px;
}

.doc-nav-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.doc-nav-btn {
  padding: 4px;
  height: 26px;
  width: 26px;
  border-radius: 4px;
  color: var(--el-text-color-regular);
}

.doc-nav-btn:hover:not(:disabled) {
  background-color: var(--el-fill-color-light);
  color: var(--el-color-primary);
}

.doc-nav-btn.is-disabled {
  color: var(--el-text-color-placeholder);
  cursor: not-allowed;
  opacity: 0.4;
}

.doc-header-path-prefix {
  font-weight: 700;
  color: var(--el-text-color-placeholder);
  user-select: none;
  font-size: 13px;
  line-height: 26px;
  margin: 0 9px 0 6px;
}

.doc-header-breadcrumb {
  line-height: 26px;
}
.recent-header-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  font-size: 11px;
  height: 20px;
  line-height: 20px;
  padding: 0 8px;
  cursor: default;
  vertical-align: middle;
}

.recent-header-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
}

.recent-header-dot.dot-upd {
  background-color: var(--el-color-primary);
  box-shadow: 0 0 4px rgba(64, 158, 255, 0.6);
}
</style>
