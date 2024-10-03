<script setup lang="jsx">
import { computed, ref, watch } from 'vue'
import { calcProjectItem, filerProjectItem } from '@/services/api/ApiProjectService'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import MoreActionsLink from '@/views/components/utils/MoreActionsLink.vue'
import CommonIcon from '@/components/common-icon/index.vue'
import { $i18nBundle } from '@/messages'

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

const treeNodes = ref([])
const defaultExpandedKeys = ref([])
const currentNodeKey = ref(null)

const calcProjectItemInfo = () => {
  const {
    docTreeNodes,
    docExpandedKeys,
    currentSelectDoc
  } = calcProjectItem(filerProjectItem(props.projectItem, searchParam.value.keyword), currentDoc.value, searchParam.value)
  currentDoc.value = currentSelectDoc
  treeNodes.value = docTreeNodes
  defaultExpandedKeys.value = docExpandedKeys
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
  if (props.projectItem) {
    calcProjectItemInfo()
  }
}, { immediate: true, deep: true })

const showDocDetails = (doc) => {
  if (doc.isDoc) {
    console.log('====================================doc', doc)
    currentDoc.value = doc
    const element = props.editable ? document.querySelector('.home-main') : document.documentElement
    element?.scrollTo({ behavior: 'smooth', top: 0 })
  }
}

const rootFolder = computed(() => {
  return props.projectItem?.folders?.find(folder => folder.rootFlag)
})

const emit = defineEmits(['toAddFolder', 'deleteFolder', 'toEditFolder', 'toAddDoc', 'toEditDoc', 'deleteDoc'])
const getFolderHandlers = folder => {
  return [{
    icon: 'FolderAdd',
    label: '新增子文件夹',
    handler: () => {
      console.log('新增文件夹', folder)
      emit('toAddFolder', folder)
    }
  }, {
    icon: 'DocumentAdd',
    label: '新建接口',
    handler: () => {
      console.log('新建接口')
      emit('toAddDoc', { folder, docType: 'api' })
    }
  }, {
    icon: 'DocumentAdd',
    label: '新建文档',
    handler: () => {
      console.log('新建文档')
      emit('toAddDoc', { folder, docType: 'md' })
    }
  }, {
    enabled: !!folder.rootFlag,
    icon: 'TextSnippetOutlined',
    labelKey: searchParam.value.showDocLabelType === 'docName' ? 'api.label.docLabelShowUrl' : 'api.label.docLabelShowName',
    handler: () => {
      searchParam.value.showDocLabelType = searchParam.value.showDocLabelType === 'url' ? 'docName' : 'url'
    }
  }, {
    enabled: !folder.rootFlag,
    icon: 'Delete',
    type: 'danger',
    label: '删除',
    handler: () => {
      console.log('删除')
      emit('deleteFolder', folder)
    }
  }]
}

const getDocHandlers = doc => {
  const isApi = doc.docType === 'api'
  const label = isApi ? '接口' : '文档'
  return [{
    icon: 'Edit',
    label: '编辑' + label,
    handler: () => {
      console.log('编辑' + label, doc)
      emit('toEditDoc', doc)
    }
  }, {
    icon: 'Delete',
    type: 'danger',
    label: '删除' + label,
    handler: () => {
      console.log('删除' + label, doc)
      emit('deleteDoc', doc)
    }
  }]
}

const calcNodeLeaf = (node) => {
  if (!node.data.isDoc) {
    return 'Folder'
  }
  return node.data.docType === 'md' ? 'Document' : 'Link'
}

const delayDropdown = ref(false)
let lastTimer = null
const leaveDropdown = (node) => { // 离开时延迟执行，方便特殊处理显示问题
  if (delayDropdown.value) {
    lastTimer = setTimeout(() => (node.data.showOperations = false), 450)
  } else {
    node.data.showOperations = false
  }
}
const enterDropdown = (node) => {
  showDropdown(node, true)
  lastTimer && clearTimeout(lastTimer) // 清理timer
}
const showDropdown = (node, delay = true) => {
  node.data.showOperations = true
  delayDropdown.value = delay
}

</script>

<template>
  <div class="padding-right2">
    <el-card
      shadow="never"
      class="small-card operation-card"
    >
      <template
        v-if="!shareDoc"
        #header
      >
        <div class="card-header">
          <span style="margin-right: auto">{{ $t('menu.label.apiManagement') }}</span>
          <span
            v-if="editable"
            class="margin-right2"
          >
            <more-actions-link :handlers="getFolderHandlers(rootFolder)" />
          </span>
        </div>
      </template>
      <el-header
        v-if="shareDoc"
        class="share-name-header"
      >
        <common-icon
          :size="30"
          icon="custom-logo"
          class="margin-right1"
        />
        <span>{{ shareDoc.shareName }}</span>
      </el-header>
      <common-form-control
        style="margin-left: -10px;"
        :option="searchFormOption"
        :model="searchParam"
      />
      <el-tree
        node-key="id"
        :data="treeNodes"
        :default-expanded-keys="defaultExpandedKeys"
        highlight-current
        :current-node-key="currentNodeKey"
        @node-click="showDocDetails"
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
                :handlers="node.data.isDoc ? getDocHandlers(node.data) : getFolderHandlers(node.data)"
                @show-dropdown="showDropdown(node)"
                @enter-dropdown="enterDropdown(node)"
                @leave-dropdown="leaveDropdown(node)"
              />
            </span>
          </div>
        </template>
      </el-tree>
    </el-card>
  </div>
</template>

<style scoped>
.share-name-header {
  display: flex;
  align-items: center;
  height: 50px;
  margin-bottom: 20px;
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
