<script setup lang="jsx">
import { computed, ref } from 'vue'
import { $i18nBundle } from '@/messages'
import CommonIcon from '@/components/common-icon/index.vue'

const props = defineProps({
  nodeKey: {
    type: String,
    default: 'value'
  },
  treeNodes: {
    type: Array,
    default: () => []
  },
  selectedKeys: {
    type: Array,
    default: () => []
  },
  treeAttrs: {
    type: Object,
    default: () => {}
  },
  showFilter: {
    type: Boolean,
    default: false
  }
})
const emit = defineEmits(['update:selectedKeys', 'submitKeys', 'filterTreeNodes'])
const showWindow = defineModel({ type: Boolean })

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

const submitKeys = () => {
  if (treeRef.value) {
    const results = [...treeRef.value?.getHalfCheckedKeys() || [], ...treeRef.value?.getCheckedKeys() || []]
    emit('update:selectedKeys', results)
    emit('submitKeys', results)
  }
  return false
}
/**
 * 过滤父节点选择状态，el-tree处理半选节点有点问题
 *
 * @type {ComputedRef<*[]>}
 */
const checkedKeys = computed(() => {
  let checkIds = []
  const selectedKeys = props.selectedKeys
  if (treeRef.value && selectedKeys) {
    checkIds = selectedKeys.filter(key => {
      const node = treeRef.value.getNode(key)
      return node && node.isLeaf
    })
    console.log('========================checkIds', checkIds, selectedKeys)
    checkIds = selectedKeys
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
        emit('filterTreeNodes', value, props.treeNodes)
      }
    },
    style: {
      flexGrow: 1,
      marginTop: '-10px'
    }
  }
})

</script>

<template>
  <common-window
    v-model="showWindow"
    default-cls="flex-column"
    v-bind="$attrs"
    :ok-click="submitKeys"
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
  </common-window>
</template>

<style scoped>

</style>
