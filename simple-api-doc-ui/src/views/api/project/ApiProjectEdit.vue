<script setup lang="jsx">
import { useRoute } from 'vue-router'
import { useBackUrl } from '@/utils'
import { ref } from 'vue'
import { useApiProjectItem } from '@/api/ApiProjectApi'
import ApiProjectImport from '@/views/components/api/ApiProjectImport.vue'
import MarkdownDocViewer from '@/views/components/api/doc/MarkdownDocViewer.vue'
import ApiDocViewer from '@/views/components/api/doc/ApiDocViewer.vue'
import ApiFolderTreeViewer from '@/views/components/api/doc/ApiFolderTreeViewer.vue'

const route = useRoute()
const projectCode = route.params.projectCode

const { goBack } = useBackUrl('/api/projects')
const { projectItem, loadSuccess, loading } = useApiProjectItem(projectCode)

const showImportWindow = ref(false)
const currentDoc = ref(null)
</script>

<template>
  <el-container class="flex-column">
    <el-page-header
      class="margin-bottom3"
      @back="goBack"
    >
      <template #content>
        <el-container>
          <span>
            {{ projectItem?.projectName }} 【{{ projectItem?.projectCode }}】
          </span>
          <span>
            <el-button
              type="success"
              @click="showImportWindow = true"
            >
              {{ $t('api.label.import') }}
            </el-button>
          </span>
        </el-container>
      </template>
    </el-page-header>
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
              editable
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
    <api-project-import
      v-model="showImportWindow"
      :project="projectItem"
    />
  </el-container>
</template>

<style scoped>

</style>
