<script setup>
defineProps({
  node: {
    type: Object,
    required: true
  },
  url: {
    type: String,
    default: ''
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
  },
  showIcon: {
    type: Boolean,
    default: true
  }
})
</script>

<template>
  <span
    class="el-tree-node__label"
    :title="node.label"
  >
    <template v-if="showIcon">
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
    </template>
    <template v-if="!url">
      <slot>
        {{ node.label }}
      </slot>
    </template>
    <div
      v-else
      class="tree-label-body"
    >
      <span class="tree-label-content">
        <slot>
          {{ node.label }}
        </slot>
      </span>
      <span
        class="tree-label-url"
        :title="url"
      >
        {{ url }}
      </span>
    </div>
  </span>
</template>

<style scoped>
.tree-label-icon {
  vertical-align: middle;
  margin-right: 4px;
  flex-shrink: 0;
}
.tree-label-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 8px;
  min-width: 0;
}
.tree-label-content {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
  overflow: hidden;
  margin-right: 8px;
}
.tree-label-content > * {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tree-label-url {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  flex-shrink: 0;
  max-width: 50%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
