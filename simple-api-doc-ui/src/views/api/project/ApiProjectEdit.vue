<script setup lang="jsx">
import { useRoute } from 'vue-router'
import { $goto, useBackUrl, calcAffixOffset } from '@/utils'
import { ref } from 'vue'
import { useApiProjectItem } from '@/api/ApiProjectApi'
import MarkdownDocViewer from '@/views/components/api/doc/MarkdownDocViewer.vue'
import ApiDocViewer from '@/views/components/api/doc/ApiDocViewer.vue'
import ApiFolderTreeViewer from '@/views/components/api/doc/ApiFolderTreeViewer.vue'

const route = useRoute()
const projectCode = route.params.projectCode

const { goBack } = useBackUrl('/api/projects')
const { projectItem, loading } = useApiProjectItem(projectCode)

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
            {{ projectItem?.projectName }}
          </span>
        </el-container>
      </template>
      <template #extra>
        <div
          v-if="projectItem"
          class="flex items-center"
        >
          <el-button
            @click="goBack"
          >
            {{ $t('common.label.back') }}
          </el-button>
          <el-button
            type="primary"
            @click="$goto(`/api/projects/shares/${projectItem.projectCode}`)"
          >
            {{ $t('api.label.shareDocs') }}
          </el-button>
          <el-button
            type="success"
            @click="$goto(`/api/projects/tasks/${projectItem.projectCode}`)"
          >
            {{ $t('api.label.importData') }}
          </el-button>
        </div>
      </template>
    </el-page-header>
    <el-container
      v-loading="loading"
    >
      <div class="form-edit-width-100">
        <common-split
          v-if="projectItem"
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
              v-model="currentDoc"
              scroll-element=".home-main"
              :scroll-element-offset-top="calcAffixOffset()"
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
