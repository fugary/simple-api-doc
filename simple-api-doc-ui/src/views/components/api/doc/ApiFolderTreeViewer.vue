<script setup>
import { ref, onMounted, computed } from 'vue'
import { calcProjectItem, filerProjectItem } from '@/services/api/ApiProjectService'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import MoreActionsLink from '@/views/components/utils/MoreActionsLink.vue'

const props = defineProps({
  projectItem: {
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
  } = calcProjectItem(filerProjectItem(props.projectItem, searchParam.value.keyword), currentDoc.value)
  currentDoc.value = currentSelectDoc
  treeNodes.value = docTreeNodes
  defaultExpandedKeys.value = docExpandedKeys
}

//* ************搜索框**************//
const searchParam = ref({})
const searchFormOption = computed(() => {
  return {
    labelWidth: '1px',
    prop: 'keyword',
    attrs: {
      onInput: calcProjectItemInfo
    }
  }
})

onMounted(() => {
  if (props.projectItem) {
    calcProjectItemInfo()
  }
})

const showDocDetails = (doc) => {
  if (doc.isDoc) {
    console.log('====================================doc', doc)
    currentDoc.value = doc
  }
}

const rootFolder = computed(() => {
  return props.projectItem?.folders?.find(folder => folder.rootFlag)
})

const getFolderHandlers = folder => {
  return [{
    icon: 'FolderAdd',
    label: '新增子文件夹',
    handler: () => {
      console.log('新增文件夹', folder)
    }
  }, {
    icon: 'DocumentAdd',
    label: '新建接口',
    handler: () => {
      console.log('新建接口')
    }
  }, {
    icon: 'DocumentAdd',
    label: '新建文档',
    handler: () => {
      console.log('新建文档')
    }
  }, {
    enabled: !folder.rootFlag,
    icon: 'Delete',
    type: 'danger',
    label: '删除',
    handler: () => {
      console.log('删除')
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
    }
  }, {
    icon: 'Delete',
    type: 'danger',
    label: '删除' + label,
    handler: () => {
      console.log('删除' + label, doc)
    }
  }]
}

</script>

<template>
  <div class="padding-right2">
    <el-card
      shadow="never"
      class="small-card operation-card"
    >
      <template #header>
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
            @mouseenter="node.data.showOperations=true"
            @mouseleave="node.data.showOperations=false"
          >
            <tree-icon-label
              :node="node"
              :icon-leaf="node.data.docType==='md'?'Document':'Link'"
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
                v-if="!node.data.isDoc"
                :handlers="getFolderHandlers(node.data)"
              />
              <more-actions-link
                v-if="node.data.isDoc"
                :handlers="getDocHandlers(node.data)"
              />
            </span>
          </div>
        </template>
      </el-tree>
    </el-card>
  </div>
</template>

<style scoped>
.custom-tree-node {
  position: relative;
  width: 100%;
}
.custom-tree-node .more-actions {
  position: absolute;
  right: 5px;
}
</style>
