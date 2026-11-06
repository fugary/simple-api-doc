<script setup>
import { computed } from 'vue'
import { getFolderPaths } from '@/services/api/ApiProjectService'

const props = defineProps({
  doc: { type: Object, default: undefined },
  folders: { type: Array, default: () => [] }
})
const folderPaths = computed(() => getFolderPaths(props.doc, props.folders))
</script>

<template>
  <div class="doc-header-nav-bar margin-top3">
    <slot />
    <span
      v-if="folderPaths.length"
      class="doc-header-path-prefix"
    >/</span>
    <el-breadcrumb
      v-if="folderPaths.length"
      class="doc-header-breadcrumb"
    >
      <el-breadcrumb-item
        v-for="(folderPath, index) in folderPaths"
        :key="index"
      >
        {{ folderPath }}
      </el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<style scoped>
.doc-header-nav-bar {
  display: flex;
  align-items: center;
  min-height: 26px;
}

.doc-header-path-prefix {
  font-weight: 700;
  color: var(--el-text-color-placeholder);
  user-select: none;
  font-size: 13px;
  line-height: 26px;
  margin: 0 9px 0 6px;
}

.doc-header-breadcrumb {
  line-height: 26px;
}
</style>
