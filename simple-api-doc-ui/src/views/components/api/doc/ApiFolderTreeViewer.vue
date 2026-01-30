<script setup lang="jsx">
import { computed, ref, reactive, watch, nextTick } from 'vue'
import {
  calcProjectItem,
  filterApiProjectItem,
  filterProjectItem,
  getFolderTreeIds
} from '@/services/api/ApiProjectService'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import MoreActionsLink from '@/views/components/utils/MoreActionsLink.vue'
import CommonIcon from '@/components/common-icon/index.vue'
import { $i18nBundle, $i18nKey } from '@/messages'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import {
  useFolderDropdown,
  getFolderHandlers,
  getDocHandlers,
  calcNodeLeaf,
  calcShowMergeAllOfHandler,
  calcShowDocLabelHandler,
  getDownloadDocsHandlers,
  calcPreferenceId,
  calcShowCleanHandlers,
  checkHasApiDoc,
  calcTreeNodeChildNodes,
  useShareDocTheme,
  isTreeNodeFirstFolder
} from '@/services/api/ApiFolderService'
import { calcProjectIconUrl, loadDetail } from '@/api/ApiProjectApi'
import { updateFolderSorts } from '@/api/ApiFolderApi'
import { useElementSize } from '@vueuse/core'
import ApiDocExportWindow from '@/views/components/api/doc/comp/ApiDocExportWindow.vue'
import { cloneDeep, debounce } from 'lodash-es'
import { $coreHideLoading, $coreShowLoading, clearAndSetValue, useReload, $coreConfirm } from '@/utils'
import ApiDocCodeGenWindow from '@/views/components/api/doc/comp/ApiDocCodeGenWindow.vue'
import { addOrEditFolderWindow } from '@/utils/DynamicUtils'

const shareConfigStore = useShareConfigStore()

const props = defineProps({
  shareDoc: {
    type: Object,
    default: undefined
  },
  editable: {
    type: Boolean,
    default: false
  },
  deletable: {
    type: Boolean,
    default: false
  },
  showClose: {
    type: Boolean,
    default: false
  }
})

const projectItem = defineModel({
  type: Object,
  default: undefined
})
const currentDoc = defineModel('currentDoc', {
  type: Object,
  default: undefined
})

const preferenceId = calcPreferenceId(projectItem.value, props.shareDoc)

const sharePreference = shareConfigStore.sharePreferenceView[preferenceId] = shareConfigStore.sharePreferenceView[preferenceId] || reactive({
  lastExpandKeys: [],
  lastDocId: undefined,
  defaultTheme: props.shareDoc?.defaultTheme || 'dark',
  defaultShowLabel: props.shareDoc?.defaultShowLabel || 'docName',
  showMergeAllOf: true,
  debugInWindow: false,
  preferenceId
})
sharePreference.preferenceId = preferenceId

const treeNodes = ref([])

const calcProjectItemInfo = () => {
  const filteredItem = filterProjectItem(projectItem.value, searchParam.value.keyword)
  const {
    docTreeNodes,
    currentSelectDoc
  } = calcProjectItem(filteredItem, searchParam.value, sharePreference)
  currentDoc.value = currentSelectDoc
  sharePreference.lastDocId = currentSelectDoc?.id
  treeNodes.value = docTreeNodes
}

//* ************搜索框**************//
const searchParam = ref({
  keyword: ''
})
const searchFormOption = computed(() => {
  return {
    labelWidth: '1px',
    prop: 'keyword',
    placeholder: $i18nBundle('common.msg.inputKeywords'),
    attrs: {
      clearable: true,
      prefixIcon: <CommonIcon icon="Search"/>
    }
  }
})
watch([searchParam, () => sharePreference?.defaultShowLabel, () => sharePreference?.showMergeAllOf], () => {
  if (projectItem.value) {
    refreshFolderTree()
  }
}, { deep: true })

const showDocDetails = (doc, edit) => {
  if (doc.isDoc) {
    console.log('====================================load doc', doc)
    if (edit) {
      treeRef.value?.setCurrentKey(doc.id)
    }
    doc.editing = !!edit
    clearAndSetValue(currentDoc, doc)
    sharePreference.lastDocId = doc.id
  }
}

const rootFolder = computed(() => {
  return projectItem.value?.folders?.find(folder => folder.rootFlag)
})

defineEmits(['toAddFolder', 'deleteFolder', 'toEditFolder', 'toAddDoc', 'toEditDoc', 'deleteDoc', 'closeLeft'])
const { enterDropdown, leaveDropdown, showDropdown } = useFolderDropdown()

const shareTopHandlers = computed(() => {
  if (rootFolder.value && hasApiDoc.value) {
    return [calcShowDocLabelHandler(rootFolder.value, sharePreference),
      calcShowMergeAllOfHandler(rootFolder.value, sharePreference),
      // calcDebugInWindowHandler(rootFolder.value, sharePreference),
      ...calcShowCleanHandlers(props.shareDoc, rootFolder.value, sharePreference, handlerData)]
  }
  return []
})

const exportTopHandlers = computed(() => getDownloadDocsHandlers(projectItem.value, props.shareDoc, handlerData))

const expandOrCollapse = (nodeData, expand) => {
  if (expand) {
    sharePreference.lastExpandKeys = [...sharePreference.lastExpandKeys, nodeData.treeId]
  } else {
    getFolderTreeIds(nodeData).forEach(nodeId => {
      sharePreference.lastExpandKeys = sharePreference.lastExpandKeys.filter(item => item !== nodeId)
    })
  }
}

const loadTreeNode = (node, resolve) => {
  if (!treeNodes.value?.length) {
    calcProjectItemInfo()
  }
  if (!node.parent) { // 第一级
    resolve(treeNodes.value)
  } else { // 下级node
    resolve(node.data?.children || [])
  }
}

const treeProps = {
  isLeaf: 'isDoc'
}

const treeRef = ref()
const showFolderTree = ref(true)

const refreshFolderTreeInternal = () => {
  showFolderTree.value = false
  nextTick(() => {
    calcProjectItemInfo()
    showFolderTree.value = true
  })
}

const refreshFolderTree = debounce(refreshFolderTreeInternal, 300)

const refreshProjectItem = (...args) => {
  $coreShowLoading({ delay: 0 })
  return new Promise((resolve, reject) => {
    if (projectItem.value?.projectCode) {
      loadDetail(projectItem.value.projectCode).then(data => {
        projectItem.value = data
        resolve(projectItem.value, ...args)
        setTimeout(() => {
          refreshFolderTreeInternal()
          $coreHideLoading()
        })
      }, reject)
    }
  })
}

const addOrEditFolder = async (id, parentFolder) => {
  addOrEditFolderWindow(id, projectItem.value.id, parentFolder, {
    onSavedFolder: () => refreshProjectItem()
  })
}

const { isDarkTheme, toggleTheme } = useShareDocTheme(sharePreference)

const currentRef = ref()
const { width } = useElementSize(currentRef)

const exportSelectedKeys = ref([])
const exportTreeNodes = ref([])
const showExportWindow = ref(false)
const currentExportType = ref('json')
const exportAllFunc = ref()
const toShowTreeConfigWindow = (type, exAllFunc) => {
  exportAllFunc.value = exAllFunc
  const { docTreeNodes } = calcProjectItem(cloneDeep(projectItem.value), null, {
    defaultShowLabel: sharePreference.defaultShowLabel
  })
  exportTreeNodes.value = docTreeNodes
  currentExportType.value = type
  showExportWindow.value = true
}

const showCodeGenConfigWindow = ref(false)
const generateTreeNodes = ref([])
const generateSelectedKeys = ref([])
const toShowCodeGenConfigWindow = () => {
  const filteredItem = filterApiProjectItem(projectItem.value)
  const { docTreeNodes } = calcProjectItem(filteredItem, null, {
    defaultShowLabel: sharePreference.defaultShowLabel
  })
  generateTreeNodes.value = docTreeNodes
  showCodeGenConfigWindow.value = true
}

const hasApiDoc = computed(() => checkHasApiDoc(projectItem.value))

const iconUrl = computed(() => calcProjectIconUrl(projectItem.value?.iconUrl))

const { reload } = useReload()

const allowDrop = (draggingNode, dropNode, type) => {
  // console.log('===========================allowDrop', draggingNode, dropNode, type)
  if (dropNode.data?.isDoc) { // 目标为文档，只能文档前后移动
    return !!draggingNode.data?.isDoc && type !== 'inner'
  } else { // 目标为文件夹只能移动到里面
    return !dropNode.data?.rootFlag && (!draggingNode.data?.isDoc || type === 'inner' || (type === 'prev' && isTreeNodeFirstFolder(dropNode) && !dropNode.parent?.data?.rootFlag))
  }
}
const allowDrag = (draggingNode) => {
  return props.editable && !draggingNode?.data?.rootFlag
}
const dragState = {
  oldParent: null,
  oldNextSibling: null
}

const handleDragStart = (node) => {
  dragState.oldParent = node.parent
  dragState.oldNextSibling = node.nextSibling
}

const handleDragEnd = (draggingNode, dropNode, type) => {
  if (!dropNode) {
    return
  }
  $coreConfirm($i18nKey('common.msg.commonConfirm', 'common.label.move'))
    .then(() => {
      const sortParam = calcTreeNodeChildNodes(dropNode, draggingNode, type)
      updateFolderSorts(sortParam).then(() => refreshProjectItem())
    }).catch(() => {
      const data = draggingNode.data
      const node = treeRef.value.getNode(data)
      treeRef.value.remove(node)
      if (dragState.oldNextSibling) {
        treeRef.value.insertBefore(data, dragState.oldNextSibling)
      } else {
        treeRef.value.append(data, dragState.oldParent)
      }
    })
}

const isDeletable = computed(() => props.deletable)

const handlerData = {
  sharePreference,
  refreshProjectItem,
  showDocDetails,
  addOrEditFolder,
  toShowTreeConfigWindow,
  toShowCodeGenConfigWindow,
  refreshFolderTree,
  reload,
  hasApiDoc,
  isDeletable
}

defineExpose(handlerData)
</script>

<template>
  <el-container
    ref="currentRef"
    class="padding-right2 flex-column"
    style="height: 100%;"
  >
    <div v-if="width>80">
      <el-header
        v-if="shareDoc"
        class="share-name-header margin-bottom3"
      >
        <img
          v-if="iconUrl"
          :src="iconUrl"
          class="api-project-icon-cls margin-right1"
          :style="width<150?'margin-right:auto;':''"
          alt="logo"
        >
        <common-icon
          v-else
          :size="30"
          icon="custom-logo"
          class="margin-right1"
          style="color: #FF821A;"
          :style="width<150?'margin-right:auto;':''"
        />
        <span
          v-if="width>=150"
          class="api-path-url"
          style="margin-right: auto;"
        >{{ shareDoc.shareName }}</span>
        <el-link
          underline="never"
          class="margin-right2"
        >
          <common-icon
            :icon="isDarkTheme ? 'sunny' : 'moon'"
            :size="20"
            @click="toggleTheme()"
          />
        </el-link>
        <more-actions-link
          v-if="exportTopHandlers?.length"
          class="margin-right2"
          :icon-size="20"
          icon="DownloadFilled"
          :handlers="exportTopHandlers"
        />
        <more-actions-link
          v-if="shareTopHandlers?.length"
          icon="Setting"
          :icon-size="20"
          :handlers="shareTopHandlers"
        />
        <el-link
          v-if="showClose"
          underline="never"
          class="margin-left2"
        >
          <common-icon
            icon="Close"
            :size="20"
            @click="$emit('closeLeft')"
          />
        </el-link>
      </el-header>
      <el-header
        v-if="editable"
        class="share-name-header"
      >
        <img
          v-if="iconUrl"
          :src="iconUrl"
          class="api-project-icon-cls margin-right1"
          :style="width<150?'margin-right:auto;':''"
          alt="logo"
        >
        <span
          class="margin-left1 api-path-url"
          style="font-size:18px;margin-right: auto"
        >{{ $t('menu.label.apiManagement') }}</span>
        <more-actions-link
          v-if="exportTopHandlers?.length"
          class="margin-right2"
          :icon-size="20"
          icon="DownloadFilled"
          :handlers="exportTopHandlers"
        />
        <more-actions-link
          v-if="shareTopHandlers?.length"
          icon="Setting"
          :icon-size="20"
          :handlers="shareTopHandlers"
        />
        <el-link
          v-if="showClose"
          underline="never"
          class="margin-left2"
        >
          <common-icon
            icon="Close"
            :size="20"
            @click="$emit('closeLeft')"
          />
        </el-link>
      </el-header>
      <common-form-control
        style="margin-left: -10px;"
        :option="searchFormOption"
        :model="searchParam"
      />
    </div>
    <el-container class="scroll-main-container">
      <el-scrollbar style="flex-grow: 1;">
        <el-tree
          v-if="showFolderTree"
          ref="treeRef"
          class="folder-schema-tree"
          :default-expand-all="!!projectItem.docs?.length && projectItem.docs.length<=20"
          node-key="treeId"
          :default-expanded-keys="sharePreference.lastExpandKeys"
          highlight-current
          :current-node-key="sharePreference.lastDocId"
          lazy
          :props="treeProps"
          :load="loadTreeNode"
          :allow-drag="allowDrag"
          :allow-drop="allowDrop"
          :draggable="editable"
          @node-drag-start="handleDragStart"
          @node-drag-end="handleDragEnd"
          @node-click="showDocDetails($event, false)"
          @node-expand="expandOrCollapse($event, true)"
          @node-collapse="expandOrCollapse($event, false)"
        >
          <template #empty>
            <el-empty :description="$t('common.msg.noData')" />
          </template>
          <template #default="{node, data}">
            <el-text
              :type="!data.enabled?'warning':''"
              class="custom-tree-node"
              :tag="!data.enabled?'del':'span'"
              @mouseenter="showDropdown(data, false)"
              @mouseleave="leaveDropdown(data)"
            >
              <tree-icon-label
                :node="node"
                :show-icon="shareDoc?.showTreeIcon!==false"
                :icon-leaf="calcNodeLeaf(data)"
              >
                <common-icon
                  v-if="editable&&data.locked"
                  v-common-tooltip="$t('api.msg.apiDocLocked')"
                  :size="18"
                  icon="LockFilled"
                  style="vertical-align: middle;"
                />
                <api-method-tag
                  v-if="data.docType==='api'"
                  :method="data.method"
                />
                {{ node.label }}
                <el-text
                  v-if="data.childDocCount&&shareDoc?.showChildrenLength!==false"
                  type="info"
                >
                  ({{ data.childDocCount }})
                </el-text>
              </tree-icon-label>
              <span
                v-if="editable"
                v-show="data.showOperations"
                class="more-actions"
              >
                <more-actions-link
                  :handlers="data.isDoc ? getDocHandlers(data, sharePreference, handlerData) : getFolderHandlers(data, sharePreference, handlerData)"
                  @show-dropdown="showDropdown(data)"
                  @enter-dropdown="enterDropdown(data)"
                  @leave-dropdown="leaveDropdown(data)"
                />
              </span>
            </el-text>
          </template>
        </el-tree>
      </el-scrollbar>
    </el-container>
    <api-doc-export-window
      v-model:tree-select-keys="exportSelectedKeys"
      v-model="showExportWindow"
      :tree-nodes="exportTreeNodes"
      :share-doc="shareDoc"
      :export-type="currentExportType"
      :project-item="projectItem"
      :export-all-func="exportAllFunc"
    />
    <api-doc-code-gen-window
      v-model:tree-select-keys="generateSelectedKeys"
      v-model="showCodeGenConfigWindow"
      :tree-nodes="generateTreeNodes"
      :tree-select-keys="generateSelectedKeys"
      :share-doc="shareDoc"
      :project-item="projectItem"
    />
  </el-container>
</template>

<style scoped>
.share-name-header {
  display: flex;
  align-items: center;
  height: 50px;
  padding: 0;
}

.custom-tree-node {
  width: 100%;
  height: 35px;
  line-height: 35px;
}

.custom-tree-node .more-actions {
  position: absolute;
  right: 10px;
  margin-top: 8px;
}
</style>
