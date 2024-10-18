<script setup lang="jsx">
import { computed, ref, reactive, watch, nextTick } from 'vue'
import { calcProjectItem, filterProjectItem, getFolderTreeIds } from '@/services/api/ApiProjectService'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import MoreActionsLink from '@/views/components/utils/MoreActionsLink.vue'
import CommonIcon from '@/components/common-icon/index.vue'
import { $i18nBundle, $i18nKey } from '@/messages'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import {
  useFolderDropdown,
  getFolderHandlers,
  getDocHandlers,
  calcNodeLeaf,
  calcShowDocLabelHandler, useFolderLayoutHeight, getChildrenSortId, getDownloadDocsHandlers
} from '@/services/api/ApiFolderService'
import { loadDetail } from '@/api/ApiProjectApi'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import ApiFolderApi from '@/api/ApiFolderApi'
import { ElMessage } from 'element-plus'
import { DEFAULT_PREFERENCE_ID_KEY } from '@/consts/ApiConstants'

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

const preferenceId = props.shareDoc?.shareId || projectItem.value?.projectCode || DEFAULT_PREFERENCE_ID_KEY

const sharePreference = shareConfigStore.sharePreferenceView[preferenceId] = shareConfigStore.sharePreferenceView[preferenceId] || reactive({
  lastExpandKeys: [],
  lastDocId: undefined,
  defaultTheme: props.shareDoc?.defaultTheme || 'dark',
  defaultShowLabel: props.shareDoc?.defaultShowLabel || 'docName'
})

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
const searchParam = ref({})
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
watch([searchParam, () => sharePreference?.defaultShowLabel], () => {
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
    sharePreference.lastDocId = doc.id
  }
}

const rootFolder = computed(() => {
  return projectItem.value?.folders?.find(folder => folder.rootFlag)
})

defineEmits(['toAddFolder', 'deleteFolder', 'toEditFolder', 'toAddDoc', 'toEditDoc', 'deleteDoc'])
const { enterDropdown, leaveDropdown, showDropdown } = useFolderDropdown()

const shareTopHandlers = computed(() => {
  if (rootFolder.value && props.shareDoc) {
    return [calcShowDocLabelHandler(rootFolder.value, sharePreference), ...getDownloadDocsHandlers(props.shareDoc)]
  }
  return []
})

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
      loadDetail(projectItem.value.projectCode).then(data => {
        projectItem.value = data
        resolve(projectItem.value, ...args)
        refreshFolderTree()
      }, reject)
    }
  })
}

const folderContainerHeight = useFolderLayoutHeight(props.editable, props.shareDoc ? -20 : 0, true)
//* *********文件夹编辑****************//
const currentEditFolder = ref()
const showEditWindow = ref(false)
const editFormOptions = [{
  labelKey: 'api.label.folderName',
  prop: 'folderName',
  placeholder: $i18nKey('common.msg.commonInput', 'api.label.folderName'),
  required: true
}]
const addOrEditFolder = async (id, parentFolder) => {
  if (id) {
    await ApiFolderApi.getById(id).then(data => {
      data.resultData && (currentEditFolder.value = data.resultData)
    })
  } else {
    currentEditFolder.value = {
      status: 1,
      projectId: projectItem.value?.id,
      parentId: parentFolder?.id,
      sortId: getChildrenSortId(parentFolder)
    }
  }
  showEditWindow.value = true
}
const saveFolder = () => {
  ApiFolderApi.saveOrUpdate({ ...currentEditFolder.value, children: undefined }, { loading: true }).then(data => {
    if (data.success) {
      ElMessage.success($i18nBundle('common.msg.saveSuccess'))
      refreshProjectItem()
      showEditWindow.value = false
    }
  })
}

if (sharePreference.defaultTheme) {
  globalConfigStore.changeTheme(sharePreference.defaultTheme === 'dark')
}

const toggleTheme = () => {
  globalConfigStore.toggleTheme()
  sharePreference.defaultTheme = globalConfigStore.isDarkTheme ? 'dark' : 'light'
}

const handlerData = {
  refreshProjectItem,
  showDocDetails,
  addOrEditFolder
}

defineExpose(handlerData)

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
          @click="toggleTheme()"
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
        <more-actions-link :handlers="getFolderHandlers(rootFolder, sharePreference, handlerData)" />
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
          class="folder-schema-tree"
          :default-expand-all="projectItem.docs?.length && projectItem.docs.length<=20"
          node-key="treeId"
          :default-expanded-keys="sharePreference.lastExpandKeys"
          highlight-current
          :current-node-key="sharePreference.lastDocId"
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
            <el-text
              :type="!data.enabled?'warning':''"
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
                <el-text
                  v-if="data.children?.length&&shareDoc?.showChildrenLength!==false"
                  type="info"
                >
                  ({{ data.children?.length }})
                </el-text>
              </tree-icon-label>
              <span
                v-if="editable"
                v-show="data.showOperations"
                class="more-actions"
              >
                <more-actions-link
                  :handlers="data.isDoc ? getDocHandlers(data, handlerData) : getFolderHandlers(data, sharePreference, handlerData)"
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
    <simple-edit-window
      v-model="currentEditFolder"
      v-model:show-edit-window="showEditWindow"
      width="500px"
      :form-options="editFormOptions"
      :name="$t('api.label.folder')"
      :save-current-item="saveFolder"
      label-width="130px"
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
  right: 5px;
  margin-top: 8px;
}
</style>
