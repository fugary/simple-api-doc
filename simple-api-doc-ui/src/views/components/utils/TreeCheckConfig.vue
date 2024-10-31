<script setup lang="jsx">
import { computed, ref } from 'vue'
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
    if (select) {
      treeKeys = getTreeKeys(props.treeNodes)
    }
    treeRef.value?.setCheckedKeys(treeKeys)
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
    console.log('========================checkIds', checkIds, selectedKeys)
  }
  return checkIds
})

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

const checkChange = debounce(() => {
  selectedKeys.value = [...treeRef.value?.getHalfCheckedKeys() || [], ...treeRef.value?.getCheckedKeys() || []]
}, 300)

const filterNode = (keyword, data) => {
  if (!keyword) return true
  return data.label?.toLowerCase()?.includes(keyword.toLowerCase())
}

</script>

<template>
  <el-container
    class="flex-column"
    :style="{ height: treeHeight}"
  >
    <el-header style="display: flex;">
      <el-button
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
          show-checkbox
          default-expand-all
          :node-key="nodeKey"
          :default-checked-keys="checkedKeys"
          :data="treeNodes"
          v-bind="treeAttrs"
          :filter-node-method="filterNode"
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

</style>
