<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { loadShare } from '@/api/SimpleShareApi'
import ApiDocViewer from '@/views/components/api/doc/ApiDocViewer.vue'
import ApiFolderTreeViewer from '@/views/components/api/doc/ApiFolderTreeViewer.vue'
import MarkdownDocViewer from '@/views/components/api/doc/MarkdownDocViewer.vue'
import { useApiProjectItem } from '@/api/ApiProjectApi'
const route = useRoute()
const shareId = route.params.shareId
const projectShare = ref()
loadShare(shareId).then(data => {
  projectShare.value = data
})
const { projectItem, loadSuccess, loading } = useApiProjectItem(null, false)
</script>

<template>
  <el-container class="flex-column">
    <el-container
      v-if="loadSuccess"
      v-loading="loading"
    >
      <div class="form-edit-width-100">
        <common-split
          :min-size="150"
          :max-size="[500, Infinity]"
        >
          <template #split-0>
            <api-folder-tree-viewer
              v-if="projectItem"
              v-model="currentDoc"
              :project-item="projectItem"
            />
          </template>
          <template #split-1>
            <markdown-doc-viewer
              v-if="currentDoc?.docType==='md'&&currentDoc?.docContent"
              v-model="currentDoc.docContent"
            />
            <api-doc-viewer
              v-if="currentDoc?.docType==='api'"
              v-model="currentDoc"
            />
          </template>
        </common-split>
      </div>
    </el-container>
  </el-container>
</template>

<style scoped>

</style>
