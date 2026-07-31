<script setup lang="jsx">
import { computed, nextTick, ref, watch } from 'vue'
import { $i18nBundle } from '@/messages'
import CommonIcon from '@/components/common-icon/index.vue'
import { debounce } from 'lodash-es'

const props = defineProps({
  nodeKey: {
    type: String,
    default: 'value'
  },
  treeNodes: {
    type: Array,
    default: () => []
  },
  treeAttrs: {
    type: Object,
    default: () => {}
  },
  treeHeight: {
    type: String,
    default: '400px'
  },
  showFilter: {
    type: Boolean,
    default: true
  },
  singleSelect: {
    type: Boolean,
    default: false
  }
})
const selectedKeys = defineModel('selectedKeys', {
  type: Array,
  default: () => []
})
const treeRef = ref(null)
const selectOrClearAll = (select) => {
  if (treeRef.value) {
    let treeKeys = []
    if (select && !props.singleSelect) {
      treeKeys = getTreeKeys(props.treeNodes)
    }
    treeRef.value?.setCheckedKeys(treeKeys)
    if (!select) {
      treeRef.value?.setCurrentKey(null)
      selectedKeys.value = []
    }
  }
}

const getTreeKeys = (nodes, keys = []) => {
  nodes.forEach(node => {
    keys.push(node[props.nodeKey])
    if (node.children) {
      getTreeKeys(node.children, keys)
    }
  })
  return keys
}

/**
 * 过滤父节点选择状态，el-tree处理半选节点有点问题
 *
 * @type {ComputedRef<*[]>}
 */
const checkedKeys = computed(() => {
  let checkIds = []
  if (treeRef.value && selectedKeys.value?.length) {
    checkIds = selectedKeys.value.filter(key => {
      const node = treeRef.value?.getNode(key)
      return node && node.isLeaf
    })
    if (props.singleSelect) {
      treeRef.value?.setCurrentKey(selectedKeys.value[0])
    }
  } else if (treeRef.value && props.singleSelect) {
    treeRef.value?.setCurrentKey(null)
  }
  return checkIds
})

watch(() => selectedKeys.value, (val) => {
  nextTick(() => {
    if (props.singleSelect && treeRef.value) {
      const activeKey = val && val.length ? val[0] : null
      treeRef.value.setCurrentKey(activeKey)
    }
  })
}, { immediate: true, deep: true })

const filterModel = ref({})
const filterOption = computed(() => {
  return {
    labelWidth: '1px',
    prop: 'keyword',
    placeholder: $i18nBundle('common.msg.inputKeywords'),
    attrs: {
      clearable: true,
      prefixIcon: <CommonIcon icon="Search"/>,
      onInput (value) {
        treeRef.value?.filter(value)
      }
    },
    style: {
      flexGrow: 1,
      marginTop: '-10px'
    }
  }
})

const nodeClick = (data, node) => {
  if (props.singleSelect && data?.isDoc) {
    if (node && node.disabled) return
    const key = data[props.nodeKey]
    selectedKeys.value = [key]
    treeRef.value?.setCurrentKey(key)
  }
}

const checkChange = debounce(() => {
  if (!props.singleSelect) {
    selectedKeys.value = [...treeRef.value?.getHalfCheckedKeys() || [], ...treeRef.value?.getCheckedKeys() || []]
  }
}, 200)

const filterNode = (keyword, data) => {
  if (!keyword) return true
  keyword = keyword.toLowerCase().trim()
  if (!keyword) return true
  if (data._searchText) {
    return data._searchText.includes(keyword)
  }
  if (data.isDoc) {
    return data.docName?.toLowerCase().includes(keyword) ||
      data.url?.toLowerCase().includes(keyword) ||
      data.method?.toLowerCase().includes(keyword)
  }
  return data.label?.toLowerCase()?.includes(keyword)
}

</script>

<template>
  <el-container
    class="flex-column"
    :style="{ height: treeHeight, maxHeight: 'calc(85vh - 160px)' }"
  >
    <el-header style="display: flex;">
      <el-button
        v-if="!singleSelect"
        type="primary"
        @click="selectOrClearAll(true)"
      >
        {{ $t('common.label.selectAll') }}
      </el-button>
      <el-button @click="selectOrClearAll(false)">
        {{ $t('common.label.clear') }}
      </el-button>
      <common-form-control
        v-if="showFilter"
        :option="filterOption"
        :model="filterModel"
      />
    </el-header>
    <el-container
      class="padding-left3"
      style="overflow: auto;"
    >
      <el-scrollbar class="form-edit-width-100 flex-column">
        <el-tree
          ref="treeRef"
          :class="[singleSelect ? 'tree-single-select' : '']"
          :show-checkbox="!singleSelect"
          highlight-current
          default-expand-all
          :node-key="nodeKey"
          :default-checked-keys="checkedKeys"
          :data="treeNodes"
          v-bind="treeAttrs"
          :filter-node-method="filterNode"
          @node-click="nodeClick"
          @check-change="checkChange"
        >
          <template #empty>
            <el-empty :description="$t('common.msg.noData')" />
          </template>
          <template #default="scope">
            <slot
              name="default"
              v-bind="scope"
            />
          </template>
        </el-tree>
      </el-scrollbar>
    </el-container>
  </el-container>
</template>

<style scoped>
:deep(.tree-single-select .el-tree-node.is-current > .el-tree-node__content) {
  border-left: 3px solid var(--el-color-primary);
}
</style>
