<script setup>
import { computed } from 'vue'
import { getFolderPaths } from '@/services/api/ApiProjectService'
defineProps({
  editable: {
    type: Boolean,
    default: false
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
</script>

<template>
  <el-header class="margin-bottom3">
    <el-breadcrumb v-if="folderPaths.length>1">
      <el-breadcrumb-item
        v-for="(folderPath, index) in folderPaths"
        :key="index"
      >
        {{ folderPath }}
      </el-breadcrumb-item>
    </el-breadcrumb>
    <h2>
      {{ currentDoc?.docName }}
      <el-button
        v-if="editable"
        class="margin-left2"
        type="primary"
        @click="currentDoc.editing=true"
      >
        {{ $t('common.label.edit') }}
      </el-button>
    </h2>
  </el-header>
</template>

<style scoped>

</style>
