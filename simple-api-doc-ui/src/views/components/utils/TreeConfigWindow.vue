<script setup lang="jsx">
import TreeCheckConfig from '@/views/components/utils/TreeCheckConfig.vue'

defineProps({
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
const emit = defineEmits(['submitKeys'])
const showWindow = defineModel({ type: Boolean })

const selectedKeys = defineModel('selectedKeys', {
  type: Array,
  default: () => []
})

const submitKeys = () => {
  emit('submitKeys', selectedKeys.value)
  return false
}
</script>

<template>
  <common-window
    v-model="showWindow"
    default-cls="flex-column"
    v-bind="$attrs"
    :ok-click="submitKeys"
  >
    <tree-check-config
      v-model:selected-keys="selectedKeys"
      :tree-nodes="treeNodes"
      :node-key="nodeKey"
      :tree-attrs="treeAttrs"
      :show-filter="showFilter"
      :tree-height="treeHeight"
    />
  </common-window>
</template>

<style scoped>

</style>
