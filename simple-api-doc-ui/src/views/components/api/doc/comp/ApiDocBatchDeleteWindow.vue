<script setup>
import { computed, ref } from 'vue'
import { isNumber } from 'lodash-es'
import { calcNodeLeaf } from '@/services/api/ApiFolderService'
import TreeConfigWindow from '@/views/components/utils/TreeConfigWindow.vue'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import CommonIcon from '@/components/common-icon/index.vue'
import ApiDocApi from '@/api/ApiDocApi'
import { $i18nBundle } from '@/messages'
import { $coreAlert, $coreConfirm, $coreHideLoading, $coreShowLoading, $coreSuccess } from '@/utils'

const props = defineProps({
  projectItem: {
    type: Object,
    default: undefined
  },
  treeNodes: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['deletedDocs'])

const showTreeConfigWindow = defineModel({
  type: Boolean,
  default: false
})

const treeSelectKeys = defineModel('treeSelectKeys', {
  type: Array,
  default: () => []
})

const keepLocked = ref(true)

const docMap = computed(() => {
  const map = new Map()
  if (props.projectItem?.docs) {
    props.projectItem.docs.forEach(doc => map.set(doc.id, doc))
  }
  return map
})

const selectedDocIds = computed(() => {
  return treeSelectKeys.value.filter(id => isNumber(id) && docMap.value.has(id))
})

const toDeleteDocIds = computed(() => {
  return keepLocked.value
    ? selectedDocIds.value.filter(id => !docMap.value.get(id)?.locked)
    : selectedDocIds.value
})

const lockedKeptCount = computed(() => {
  return keepLocked.value ? selectedDocIds.value.length - toDeleteDocIds.value.length : 0
})

const okLabel = computed(() => {
  const count = toDeleteDocIds.value.length
  return count > 0
    ? `${$i18nBundle('api.label.deleteSelected')} (${count})`
    : $i18nBundle('api.label.deleteSelected')
})

const deleteSelectedDocs = () => {
  const totalSelected = selectedDocIds.value.length
  const toDelete = toDeleteDocIds.value
  const kept = lockedKeptCount.value

  if (totalSelected === 0) {
    $coreAlert($i18nBundle('api.msg.noDocSelectedToDelete'))
    return
  }
  if (toDelete.length === 0 && kept > 0) {
    $coreAlert($i18nBundle('api.msg.allSelectedDocsLocked'))
    return
  }

  const confirmMsg = kept > 0
    ? $i18nBundle('api.msg.batchDeleteDocConfirmWithLocked', [toDelete.length, kept])
    : $i18nBundle('api.msg.batchDeleteDocConfirm', [toDelete.length])

  $coreConfirm(confirmMsg).then(() => {
    $coreShowLoading()
    ApiDocApi.removeByIds(toDelete, { loading: false })
      .then(() => {
        $coreSuccess($i18nBundle('common.msg.deleteSuccess'))
        showTreeConfigWindow.value = false
        emit('deletedDocs', toDelete)
      })
      .finally(() => {
        $coreHideLoading()
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
    :title="$t('api.label.batchDeleteDocs')"
    :ok-label="okLabel"
    ok-type="danger"
    width="950px"
    @submit-keys="deleteSelectedDocs"
  >
    <template #extra-filter>
      <el-tooltip
        :content="$t('api.msg.keepLockedDocsTooltip')"
        placement="top"
      >
        <el-checkbox
          v-model="keepLocked"
        >
          {{ $t('api.label.keepLockedDocs') }}
        </el-checkbox>
      </el-tooltip>
    </template>
    <template #default="{node, data}">
      <el-text
        :type="!data.enabled ? 'danger' : (data.deprecated ? 'warning' : '')"
        class="custom-tree-node"
      >
        <tree-icon-label
          :node="node"
          :icon-leaf="calcNodeLeaf(data)"
          :url="data.isDoc ? data.url : ''"
        >
          <common-icon
            v-if="data.locked"
            v-common-tooltip="$t('api.msg.apiDocLocked')"
            :size="16"
            icon="LockFilled"
            style="vertical-align: middle; margin-right: 4px;"
          />
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

<style scoped>

</style>
