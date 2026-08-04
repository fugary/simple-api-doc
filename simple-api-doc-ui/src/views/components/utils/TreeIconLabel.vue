<script setup>
import { computed } from 'vue'

const props = defineProps({
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

const leafIconClass = computed(() => {
  const icon = props.iconLeaf
  if (icon === 'custom-markdown' || icon === 'Document' || icon === 'md') {
    return 'md-icon'
  }
  if (icon === 'custom-api' || icon === 'api') {
    return 'api-icon'
  }
  if (icon === 'Folder' || icon === 'FolderOpened' || icon?.startsWith('Folder') || props.node?.data?.isDoc === false) {
    return 'folder-icon'
  }
  return 'default-leaf-icon'
})
</script>

<template>
  <span
    class="el-tree-node__label"
    :class="{ 'has-url': !!url }"
    :title="node.label"
  >
    <template v-if="showIcon">
      <common-icon
        v-if="!node.isLeaf&&node.expanded"
        class="tree-label-icon folder-icon"
        :size="iconSize"
        :icon="iconExpand"
      />
      <common-icon
        v-if="!node.isLeaf&&!node.expanded"
        class="tree-label-icon folder-icon"
        :size="iconSize"
        :icon="iconClosed"
      />
      <common-icon
        v-if="node.isLeaf"
        class="tree-label-icon"
        :class="leafIconClass"
        :size="iconSize"
        :icon="iconLeaf"
      />
    </template>
    <span
      v-if="!url"
      class="tree-label-content"
    >
      <slot>
        {{ node.label }}
      </slot>
    </span>
    <span
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
    </span>
  </span>
</template>

<style scoped>
.el-tree-node__label {
  display: inline-flex;
  align-items: center;
}
.el-tree-node__label.has-url {
  flex: 1;
  min-width: 0;
}
.tree-label-icon {
  vertical-align: middle;
  margin-right: 4px;
  flex-shrink: 0;
}
.tree-label-icon.folder-icon {
  color: #3b82f6;
}
.tree-label-icon.md-icon {
  color: #8b5cf6;
}
.tree-label-icon.api-icon {
  color: #10b981;
}
.tree-label-body {
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 8px;
  min-width: 0;
  flex: 1;
}
.tree-label-content {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}
.tree-label-body .tree-label-content {
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
