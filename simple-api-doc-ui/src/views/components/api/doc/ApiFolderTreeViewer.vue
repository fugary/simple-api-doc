<script setup lang="jsx">
import { computed, ref, reactive } from 'vue'
import { calcProjectItem, filterProjectItem, getFolderIds } from '@/services/api/ApiProjectService'
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
  calcShowDocLabelHandler
} from '@/services/api/ApiFolderService'

const globalConfigStore = useGlobalConfigStore()
const shareConfigStore = useShareConfigStore()

const props = defineProps({
  projectItem: {
    type: Object,
    default: undefined
  },
  shareDoc: {
    type: Object,
    default: undefined
  },
  editable: {
    type: Boolean,
    default: false
  }
})

const currentDoc = defineModel({
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
  const filteredItem = filterProjectItem(props.projectItem, searchParam.value.keyword)
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
// watch(searchParam, () => {
//   if (props.projectItem) {
//     calcProjectItemInfo()
//   }
// }, { deep: true })

const showDocDetails = (doc) => {
  if (doc.isDoc) {
    console.log('====================================doc', doc)
    currentDoc.value = doc
    folderView.currentDocId = doc.id
    const element = props.editable ? document.querySelector('.home-main') : document.documentElement
    element?.scrollTo({ behavior: 'smooth', top: 0 })
  }
}

const rootFolder = computed(() => {
  return props.projectItem?.folders?.find(folder => folder.rootFlag)
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
    folderView.expandedKeys = [...folderView.expandedKeys, nodeData.id]
  } else {
    getFolderIds(nodeData).forEach(nodeId => {
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
        <more-actions-link :handlers="getFolderHandlers(rootFolder, searchParam, $emit)" />
      </span>
    </el-header>
    <common-form-control
      style="margin-left: -10px;"
      :option="searchFormOption"
      :model="searchParam"
    />
    <el-tree
      node-key="id"
      :default-expanded-keys="folderView.expandedKeys"
      highlight-current
      :current-node-key="folderView.currentDocId"
      lazy
      :props="treeProps"
      :load="loadTreeNode"
      @node-click="showDocDetails"
      @node-expand="expandOrCollapse($event, true)"
      @node-collapse="expandOrCollapse($event, false)"
    >
      <template #empty>
        <el-empty :description="$t('common.msg.noData')" />
      </template>
      <template #default="{node}">
        <div
          class="custom-tree-node"
          @mouseenter="showDropdown(node, false)"
          @mouseleave="leaveDropdown(node)"
        >
          <tree-icon-label
            :node="node"
            :icon-leaf="calcNodeLeaf(node)"
          >
            <api-method-tag
              v-if="node.data.docType==='api'"
              :method="node.data.method"
            />
            {{ node.label }}
          </tree-icon-label>
          <span
            v-if="editable"
            v-show="node.data.showOperations"
            class="more-actions"
          >
            <more-actions-link
              :handlers="node.data.isDoc ? getDocHandlers(node.data, $emit) : getFolderHandlers(node.data, searchParam, $emit)"
              @show-dropdown="showDropdown(node)"
              @enter-dropdown="enterDropdown(node)"
              @leave-dropdown="leaveDropdown(node)"
            />
          </span>
        </div>
      </template>
    </el-tree>
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
