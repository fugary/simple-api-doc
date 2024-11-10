<script setup>
import { ref, computed } from 'vue'
import { getFolderPaths } from '@/services/api/ApiProjectService'
import ApiDocHistoryViewer from '@/views/components/api/doc/comp/ApiDocHistoryViewer.vue'
defineProps({
  editable: {
    type: Boolean,
    default: false
  },
  historyCount: {
    type: Number,
    default: 0
  }
})
const currentDoc = defineModel({
  type: Object,
  default: undefined
})
const folderPaths = computed(() => {
  if (currentDoc.value) {
    return getFolderPaths(currentDoc.value)
  }
  return []
})
const apiDocHistoryRef = ref()
</script>

<template>
  <el-header style="min-height: var(--el-header-height);height:auto;">
    <el-breadcrumb v-if="folderPaths.length>1">
      <el-breadcrumb-item
        v-for="(folderPath, index) in folderPaths"
        :key="index"
      >
        {{ folderPath }}
      </el-breadcrumb-item>
    </el-breadcrumb>
    <h2>
      {{ currentDoc?.docName || currentDoc?.url }}
      <el-button
        v-if="editable"
        class="margin-left2"
        type="primary"
        @click="currentDoc.editing=true"
      >
        {{ $t('common.label.edit') }}
      </el-button>
      <el-link
        v-if="editable&&historyCount"
        class="margin-left2"
        type="primary"
        @click="apiDocHistoryRef?.showHistoryList(currentDoc.id)"
      >
        {{ $t('api.label.historyVersions') }}
        <el-text type="info">
          ({{ historyCount }})
        </el-text>
      </el-link>
      <api-doc-history-viewer
        v-if="historyCount"
        ref="apiDocHistoryRef"
      />
    </h2>
  </el-header>
</template>

<style scoped>

</style>
