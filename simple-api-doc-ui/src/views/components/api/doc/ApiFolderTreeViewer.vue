<script setup lang="jsx">
import { computed, ref, reactive, watch, nextTick } from 'vue'
import { calcProjectItem, filterProjectItem, getFolderTreeIds } from '@/services/api/ApiProjectService'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import MoreActionsLink from '@/views/components/utils/MoreActionsLink.vue'
import CommonIcon from '@/components/common-icon/index.vue'
import { $i18nBundle } from '@/messages'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import {
  useFolderDropdown,
  getFolderHandlers,
  getDocHandlers,
  calcNodeLeaf,
  calcShowDocLabelHandler, useFolderLayoutHeight
} from '@/services/api/ApiFolderService'
import { loadByCode } from '@/api/ApiProjectApi'

const globalConfigStore = useGlobalConfigStore()
const shareConfigStore = useShareConfigStore()

const props = defineProps({
  shareDoc: {
    type: Object,
    default: undefined
  },
  editable: {
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
let folderView = reactive({
  expandedKeys: [],
  currentDocId: undefined
})
if (props.shareDoc) {
  folderView = shareConfigStore.shareFolderView[props.shareDoc.shareId] = shareConfigStore.shareFolderView[props.shareDoc.shareId] || folderView
}
const treeNodes = ref([])

const calcProjectItemInfo = () => {
  const filteredItem = filterProjectItem(projectItem.value, searchParam.value.keyword)
  const {
    docTreeNodes,
    currentSelectDoc
  } = calcProjectItem(filteredItem, searchParam.value, folderView.expandedKeys, folderView.currentDocId)
  currentDoc.value = currentSelectDoc
  folderView.currentDocId = currentSelectDoc?.id
  treeNodes.value = docTreeNodes
}

//* ************搜索框**************//
const searchParam = ref({
  showDocLabelType: 'docName'
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
watch(searchParam, () => {
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
    currentDoc.value = doc
    folderView.currentDocId = doc.id
  }
}

const rootFolder = computed(() => {
  return projectItem.value?.folders?.find(folder => folder.rootFlag)
})

defineEmits(['toAddFolder', 'deleteFolder', 'toEditFolder', 'toAddDoc', 'toEditDoc', 'deleteDoc'])
const { enterDropdown, leaveDropdown, showDropdown } = useFolderDropdown()

const shareTopHandlers = computed(() => {
  if (rootFolder.value) {
    return [calcShowDocLabelHandler(rootFolder.value, searchParam.value)]
  }
  return []
})

const expandOrCollapse = (nodeData, expand) => {
  if (expand) {
    folderView.expandedKeys = [...folderView.expandedKeys, nodeData.treeId]
  } else {
    getFolderTreeIds(nodeData).forEach(nodeId => {
      folderView.expandedKeys = folderView.expandedKeys.filter(item => item !== nodeId)
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

const refreshFolderTree = () => {
  showFolderTree.value = false
  nextTick(() => {
    calcProjectItemInfo()
    showFolderTree.value = true
  })
}

const refreshProjectItem = (...args) => {
  return new Promise((resolve, reject) => {
    if (projectItem.value?.projectCode) {
      loadByCode(projectItem.value.projectCode).then(data => {
        projectItem.value = data
        resolve(projectItem.value, ...args)
        refreshFolderTree()
      }, reject)
    }
  })
}

const handlerData = {
  refreshProjectItem,
  showDocDetails
}

defineExpose(handlerData)

const folderContainerHeight = useFolderLayoutHeight(props.editable)

</script>

<template>
  <el-container class="padding-right2">
    <el-header
      v-if="shareDoc"
      class="share-name-header margin-bottom3"
    >
      <common-icon
        :size="30"
        icon="custom-logo"
        class="margin-right1"
        style="color: #FF821A;"
      />
      <span style="margin-right: auto;">{{ shareDoc.shareName }}</span>
      <el-link
        :underline="false"
        class="margin-right2"
      >
        <common-icon
          :icon="globalConfigStore.isDarkTheme ? 'sunny' : 'moon'"
          :size="20"
          @click="globalConfigStore.toggleTheme"
        />
      </el-link>
      <more-actions-link
        :icon-size="20"
        :handlers="shareTopHandlers"
      />
    </el-header>
    <el-header
      v-if="editable"
      class="share-name-header"
    >
      <span
        class="margin-left1"
        style="font-size:18px;margin-right: auto"
      >{{ $t('menu.label.apiManagement') }}</span>
      <span class="margin-right2">
        <more-actions-link :handlers="getFolderHandlers(rootFolder, searchParam, handlerData)" />
      </span>
    </el-header>
    <common-form-control
      style="margin-left: -10px;"
      :option="searchFormOption"
      :model="searchParam"
    />
    <el-container :style="{height:folderContainerHeight}">
      <el-scrollbar style="flex-grow: 1;">
        <el-tree
          v-if="showFolderTree"
          ref="treeRef"
          node-key="treeId"
          :default-expanded-keys="folderView.expandedKeys"
          highlight-current
          :current-node-key="folderView.currentDocId"
          lazy
          :props="treeProps"
          :load="loadTreeNode"
          @node-click="showDocDetails($event, false)"
          @node-expand="expandOrCollapse($event, true)"
          @node-collapse="expandOrCollapse($event, false)"
        >
          <template #empty>
            <el-empty :description="$t('common.msg.noData')" />
          </template>
          <template #default="{node, data}">
            <div
              class="custom-tree-node"
              @mouseenter="showDropdown(data, false)"
              @mouseleave="leaveDropdown(data)"
            >
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
              <span
                v-if="editable"
                v-show="data.showOperations"
                class="more-actions"
              >
                <more-actions-link
                  :handlers="data.isDoc ? getDocHandlers(data, handlerData) : getFolderHandlers(data, searchParam, handlerData)"
                  @show-dropdown="showDropdown(data)"
                  @enter-dropdown="enterDropdown(data)"
                  @leave-dropdown="leaveDropdown(data)"
                />
              </span>
            </div>
          </template>
        </el-tree>
      </el-scrollbar>
    </el-container>
  </el-container>
</template>

<style scoped>
.share-name-header {
  display: flex;
  align-items: center;
  height: 50px;
}

.custom-tree-node {
  width: 100%;
  height: 25px;
}

.custom-tree-node .more-actions {
  position: absolute;
  right: 5px;
}
</style>
