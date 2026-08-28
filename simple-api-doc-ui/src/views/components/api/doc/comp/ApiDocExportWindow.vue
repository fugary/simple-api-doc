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
import { computed, ref } from 'vue'

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

const exportZip = ref(true)
const withFrontmatter = ref(true)

const exportDocIds = computed(() => {
  return treeSelectKeys.value.filter(id => isNumber(id)) || []
})

const exportSelectedDocs = () => {
  const docIds = exportDocIds.value
  let type = props.exportType
  if (type === 'md' && exportZip.value) {
    type = 'zip'
  }
  const isShareDoc = !!props.shareDoc?.shareId
  const param = {
    shareId: props.shareDoc?.shareId,
    projectCode: props.projectItem?.projectCode,
    type,
    withFrontmatter: withFrontmatter.value
  }
  if (docIds.length) {
    param.docIds = docIds
  } else if (!props.exportAllFunc) {
    $coreAlert($i18nBundle('api.msg.noApiSelected'))
    return
  }
  $coreConfirm($i18nBundle('api.msg.exportConfirm')).then(() => {
    const checkDownloadFunc = isShareDoc ? checkExportDownloadDocs : checkExportProjectDocs
    const downloadExportFunc = isShareDoc ? downloadExportShareDocs : downloadExportProjectDocs
    checkDownloadFunc(param).then(resData => {
      if (resData.success && resData.resultData) {
        downloadExportFunc({
          ...param, uuid: resData.resultData
        })
      }
    })
  })
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
    <template
      v-if="exportType === 'md'"
      #extra-filter
    >
      <el-tooltip
        :content="$t('api.msg.exportZipTooltip')"
        placement="top"
      >
        <el-checkbox
          v-model="exportZip"
          class="margin-left2"
        >
          {{ $t('api.label.exportZip') }}
        </el-checkbox>
      </el-tooltip>
      <el-tooltip
        v-if="exportZip"
        :content="$t('api.msg.withFrontmatterTooltip')"
        placement="top"
      >
        <el-checkbox
          v-model="withFrontmatter"
          class="margin-left2"
        >
          {{ $t('api.label.withFrontmatter') }}
        </el-checkbox>
      </el-tooltip>
    </template>
    <template #default="{node, data}">
      <el-text
        :type="!data.enabled ? 'danger' : (data.deprecated ? 'warning' : '')"
        class="custom-tree-node"
      >
        <tree-icon-label
          :show-icon="shareDoc?.showTreeIcon!==false"
          :node="node"
          :icon-leaf="calcNodeLeaf(data)"
          :url="data.isDoc ? data.url : ''"
        >
          <api-method-tag
            v-if="data.docType==='api'"
            :method="data.method"
          />
          <del v-if="data.deprecated">{{ data.docName || node.label }}</del>
          <span v-else>{{ data.docName || node.label }}</span>
        </tree-icon-label>
      </el-text>
    </template>
  </tree-config-window>
</template>
