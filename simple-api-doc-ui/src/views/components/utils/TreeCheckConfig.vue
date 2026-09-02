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
const onlyApi = ref(false)
const onlyDoc = ref(false)

const hasTypeFilter = computed(() => onlyApi.value || onlyDoc.value)
const isFiltering = computed(() => !!filterModel.value?.keyword?.trim() || onlySelected.value || hasTypeFilter.value)

// 检查节点是否被禁用
const isNodeDisabled = (data) => {
  if (!data) return false
  const treeNode = treeRef.value?.getNode(data[props.nodeKey])
  if (typeof treeNode?.disabled === 'boolean') {
    return treeNode.disabled
  }
  const disabledProp = props.treeAttrs?.props?.disabled
  if (typeof disabledProp === 'function') {
    return Boolean(disabledProp(data, treeNode))
  }
  if (typeof disabledProp === 'string') {
    return Boolean(data[disabledProp])
  }
  return Boolean(data.disabled)
}

// 检查当前树中是否同时存在可选的 API 和 MD 文档
const hasBothTypes = computed(() => {
  let hasApi = false
  let hasDoc = false
  const check = (nodes) => {
    if (!nodes || (hasApi && hasDoc)) return
    for (const n of nodes) {
      if (!isNodeDisabled(n)) {
        if (n.docType === 'api') hasApi = true
        else if (n.docType === 'md') hasDoc = true
      }
      if (hasApi && hasDoc) return
      if (n.children?.length) check(n.children)
    }
  }
  check(props.treeNodes)
  return hasApi && hasDoc
})

const applyCheckedKeys = (keys) => {
  isInternalChange = true
  treeRef.value?.setCheckedKeys(keys)
  selectedKeys.value = [
    ...treeRef.value?.getHalfCheckedKeys() || [],
    ...treeRef.value?.getCheckedKeys() || []
  ]
}

/**
 * 递归收集节点列表中非禁用的叶子节点 key
 */
const getSelectableLeafKeys = (nodes, onlyVisible = false, result = []) => {
  if (!nodes) return result
  nodes.forEach(node => {
    const isLeaf = !node.children || node.children.length === 0
    if (isLeaf) {
      if (!isNodeDisabled(node)) {
        const treeNode = treeRef.value?.getNode(node[props.nodeKey])
        if (!onlyVisible || (treeNode ? treeNode.visible : true)) {
          result.push(node[props.nodeKey])
        }
      }
    } else if (node.children) {
      getSelectableLeafKeys(node.children, onlyVisible, result)
    }
  })
  return result
}

const selectOrClearAll = (select) => {
  if (!treeRef.value) return

  // 获取原本已选中的禁用节点 key，确保操作时保持其状态
  const disabledCheckedKeys = checkedKeys.value.filter(key => isNodeDisabled(treeRef.value?.getNode(key)?.data))

  if (!select) {
    treeRef.value.setCurrentKey(null)
    applyCheckedKeys(disabledCheckedKeys)
    return
  }

  if (!props.singleSelect) {
    const targetKeys = isFiltering.value
      ? [...new Set([...checkedKeys.value, ...getSelectableLeafKeys(props.treeNodes, true)])]
      : [...new Set([...disabledCheckedKeys, ...getSelectableLeafKeys(props.treeNodes, false)])]
    applyCheckedKeys(targetKeys)
  }
}

/**
 * 检查节点是否满足类型过滤条件
 */
const matchesTypeFilter = (node) => {
  if (!hasTypeFilter.value) return true
  if (onlyApi.value && node.docType === 'api') return true
  if (onlyDoc.value && node.docType === 'md') return true
  return false
}

/**
 * 过滤父节点选择状态，el-tree处理半选节点有点问题
 *
 * @type {ComputedRef<*[]>}
 */
const checkedKeys = computed(() => {
  if (!treeRef.value || !selectedKeys.value?.length) return []
  return selectedKeys.value.filter(key => {
    const node = treeRef.value?.getNode(key)
    return node && node.isLeaf
  })
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
        const isSelected = set.has(node[props.nodeKey])
        const disabled = isNodeDisabled(node)

        // 仅将可选（非禁用）或已选中的叶子节点计入总数
        if (!disabled || isSelected) {
          totalLeaf++
        }

        const isApi = node.docType === 'api'
        const isMd = node.docType === 'md'

        if (isSelected) {
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

let isInternalChange = false

watch(() => selectedKeys.value, (val) => {
  if (!isInternalChange) {
    nextTick(() => {
      if (treeRef.value) {
        if (props.singleSelect) {
          treeRef.value.setCurrentKey(val?.length ? val[0] : null)
        } else {
          treeRef.value.setCheckedKeys(checkedKeys.value)
        }
      }
    })
  }
  isInternalChange = false

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

const handleOnlyApiChange = (val) => {
  if (val) {
    onlyDoc.value = false
  }
  treeRef.value?.filter(filterModel.value.keyword)
}

const handleOnlyDocChange = (val) => {
  if (val) {
    onlyApi.value = false
  }
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
    isInternalChange = true
    selectedKeys.value = [...treeRef.value?.getHalfCheckedKeys() || [], ...treeRef.value?.getCheckedKeys() || []]
  }
}, 200)

/**
 * 处理用户手动点击节点复选框
 * 当处于过滤筛选状态时，如果点击的是文件夹节点，只选中/取消选中当前筛选可见的子项，隐藏子项保持原状
 */
const handleCheck = (data) => {
  if (props.singleSelect || !isFiltering.value || !data.children?.length) return

  const visibleKeys = getSelectableLeafKeys(data.children, true)
  if (!visibleKeys.length) {
    applyCheckedKeys(checkedKeys.value)
    return
  }

  // 检查可见子项是否已全部选中：若是则全部取消，否则全部选中
  const checkedSet = new Set(checkedKeys.value)
  const allChecked = visibleKeys.every(k => checkedSet.has(k))

  visibleKeys.forEach(k => {
    if (allChecked) {
      checkedSet.delete(k)
    } else {
      checkedSet.add(k)
    }
  })

  applyCheckedKeys([...checkedSet])
}

const filterNode = (value, data) => {
  const isLeaf = !data.children || data.children.length === 0

  if (isLeaf) {
    // 仅看已选：只显示已选中的叶子节点
    if (onlySelected.value && !selectedKeysSet.value.has(data[props.nodeKey])) {
      return false
    }

    // 类型过滤：仅接口 / 仅文档
    if (hasTypeFilter.value && !matchesTypeFilter(data)) {
      return false
    }

    const keyword = (value || '').toLowerCase().trim()
    if (!keyword) return true
    if (data._searchText?.includes(keyword)) {
      return true
    }
    if (data.isDoc) {
      return data.docName?.toLowerCase().includes(keyword) ||
        data.url?.toLowerCase().includes(keyword) ||
        data.method?.toLowerCase().includes(keyword)
    }
    return data.label?.toLowerCase()?.includes(keyword)
  }

  // 文件夹节点：当存在任何过滤条件时返回 false，由 el-tree 回溯机制根据其是否有可见子节点自动判定
  return !isFiltering.value
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
          <template v-if="hasBothTypes">
            <el-checkbox
              v-model="onlyApi"
              @change="handleOnlyApiChange"
            >
              {{ $t('common.label.onlyApi') }}
            </el-checkbox>
            <el-checkbox
              v-model="onlyDoc"
              @change="handleOnlyDocChange"
            >
              {{ $t('common.label.onlyDoc') }}
            </el-checkbox>
          </template>
          <slot name="extra-filter" />
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
          @check="handleCheck"
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
  gap: 12px;
  width: 100%;
  padding: 2px 0 4px 0;
  font-size: 13px;
}

.tree-check-filter-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.tree-check-filter-group :deep(.el-checkbox) {
  margin-right: 0 !important;
}

.tree-check-filter-group :deep(.margin-left2) {
  margin-left: 0 !important;
}

.tree-check-stats-group {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-regular);
  flex-shrink: 0;
  white-space: nowrap;
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
