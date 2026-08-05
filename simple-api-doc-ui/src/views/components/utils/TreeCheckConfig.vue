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
const onlySelected = ref(false)

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

const selectedKeysSet = computed(() => {
  return new Set(selectedKeys.value || [])
})

const selectedStats = computed(() => {
  let totalLeaf = 0
  let totalSelected = 0
  let apiSelected = 0
  let mdSelected = 0

  const set = selectedKeysSet.value

  const traverse = (nodes) => {
    if (!nodes || !nodes.length) return
    nodes.forEach(node => {
      const isLeaf = !node.children || node.children.length === 0
      if (isLeaf) {
        totalLeaf++
        const isApi = node.docType === 'api'
        const isMd = node.docType === 'md'

        if (set.has(node[props.nodeKey])) {
          totalSelected++
          if (isApi) apiSelected++
          if (isMd) mdSelected++
        }
      } else if (node.children) {
        traverse(node.children)
      }
    })
  }

  traverse(props.treeNodes)
  return {
    totalLeaf,
    totalSelected,
    apiSelected,
    mdSelected
  }
})

watch(() => selectedKeys.value, (val) => {
  nextTick(() => {
    if (props.singleSelect && treeRef.value) {
      const activeKey = val && val.length ? val[0] : null
      treeRef.value.setCurrentKey(activeKey)
    }
  })
  if (onlySelected.value) {
    treeRef.value?.filter(filterModel.value.keyword)
  }
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
      flexGrow: 1
    }
  }
})

const handleOnlySelectedChange = () => {
  treeRef.value?.filter(filterModel.value.keyword)
}

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

const filterNode = (value, data) => {
  const isLeaf = !data.children || data.children.length === 0
  if (onlySelected.value && (!isLeaf || !selectedKeysSet.value.has(data[props.nodeKey]))) {
    return false
  }

  const keyword = (value || '').toLowerCase().trim()
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
    <el-header class="tree-check-header">
      <div class="tree-check-row-top">
        <div class="tree-check-actions">
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
        </div>
        <common-form-control
          v-if="showFilter"
          :option="filterOption"
          :model="filterModel"
        />
      </div>
      <div
        v-if="!singleSelect"
        class="tree-check-row-bottom"
      >
        <div class="tree-check-filter-group">
          <el-checkbox
            v-model="onlySelected"
            @change="handleOnlySelectedChange"
          >
            {{ $t('common.label.onlySelected') }}
          </el-checkbox>
        </div>
        <div class="tree-check-stats-group">
          <template v-if="selectedStats.apiSelected > 0 || selectedStats.mdSelected > 0">
            <el-tag
              v-if="selectedStats.apiSelected > 0"
              size="small"
              type="success"
              effect="plain"
              class="stats-tag"
            >
              {{ $t('common.label.apiCount', [selectedStats.apiSelected]) }}
            </el-tag>
            <el-tag
              v-if="selectedStats.mdSelected > 0"
              size="small"
              type="warning"
              effect="plain"
              class="stats-tag"
            >
              {{ $t('common.label.docCount', [selectedStats.mdSelected]) }}
            </el-tag>
            <span class="stats-divider">|</span>
          </template>
          <span class="stats-label">
            {{ selectedStats.totalLeaf > 0 ? $t('common.label.selectedCount', [selectedStats.totalSelected, selectedStats.totalLeaf]) : $t('common.label.selectedCountSimple', [selectedStats.totalSelected]) }}
          </span>
        </div>
      </div>
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
            <el-empty :description="onlySelected ? ($t('api.msg.noApiSelected') || $t('common.msg.noData')) : $t('common.msg.noData')" />
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
.tree-check-header {
  display: flex;
  flex-direction: column;
  height: auto !important;
  padding: 0 0 10px 0;
  gap: 10px;
}

.tree-check-row-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.tree-check-row-top :deep(.el-form-item) {
  margin-bottom: 0 !important;
}

.tree-check-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tree-check-row-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 2px 0 4px 0;
  font-size: 13px;
}

.tree-check-filter-group {
  display: flex;
  align-items: center;
}

.tree-check-stats-group {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-regular);
}

.stats-label {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.stats-divider {
  color: var(--el-border-color);
  margin: 0 4px;
}

.stats-tag {
  border-radius: 4px;
}
</style>
