<script setup>
defineProps({
  node: {
    type: Object,
    required: true
  },
  iconSize: {
    type: [Number, String],
    default: 18
  },
  iconExpand: {
    type: String,
    default: 'FolderOpened'
  },
  iconClosed: {
    type: String,
    default: 'Folder'
  },
  iconLeaf: {
    type: String,
    default: 'InsertDriveFileOutlined'
  }
})
</script>

<template>
  <span
    class="el-tree-node__label"
    :title="node.label"
  >
    <common-icon
      v-if="!node.isLeaf&&node.expanded"
      class="tree-label-icon"
      :size="iconSize"
      :icon="iconExpand"
    />
    <common-icon
      v-if="!node.isLeaf&&!node.expanded"
      class="tree-label-icon"
      :size="iconSize"
      :icon="iconClosed"
    />
    <common-icon
      v-if="node.isLeaf"
      class="tree-label-icon"
      :size="iconSize"
      :icon="iconLeaf"
    />
    <slot>
      {{ node.label }}
    </slot>
  </span>
</template>

<style scoped>
.tree-label-icon {
  vertical-align: bottom;
  margin-right: 4px;
}
</style>
