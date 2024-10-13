<script setup lang="jsx">
import { useRoute } from 'vue-router'
import { $goto, useBackUrl } from '@/utils'
import { ref } from 'vue'
import { useApiProjectItem } from '@/api/ApiProjectApi'
import MarkdownDocViewer from '@/views/components/api/doc/MarkdownDocViewer.vue'
import ApiDocViewer from '@/views/components/api/doc/ApiDocViewer.vue'
import ApiFolderTreeViewer from '@/views/components/api/doc/ApiFolderTreeViewer.vue'
import MarkdownDocEditor from '@/views/components/api/doc/MarkdownDocEditor.vue'

const route = useRoute()
const projectCode = route.params.projectCode

const { goBack } = useBackUrl('/api/projects')
const { projectItem, loading } = useApiProjectItem(projectCode)

const folderTreeRef = ref()
const currentDoc = ref(null)
const savedApiDoc = () => {
  currentDoc.value.editing = false
  folderTreeRef.value?.refreshProjectItem()
}
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
          <el-badge
            :value="projectItem.shares?.filter(share=>share.status).length"
            :show-zero="false"
            type="primary"
            class="padding-left2"
          >
            <el-button
              type="primary"
              class="padding-left1"
              @click="$goto(`/api/projects/shares/${projectItem.projectCode}`)"
            >
              <common-icon
                icon="Share"
                class="margin-right1"
              />
              {{ $t('api.label.shareDocs') }}
            </el-button>
          </el-badge>
          <el-badge
            :value="projectItem.tasks?.filter(task=>task.status).length"
            :show-zero="false"
            type="success"
            class="padding-left2"
          >
            <el-button
              type="success"
              class="padding-left1"
              @click="$goto(`/api/projects/tasks/${projectItem.projectCode}`)"
            >
              <common-icon
                icon="InputFilled"
                class="margin-right1"
              />
              {{ $t('api.label.importData') }}
            </el-button>
          </el-badge>
        </div>
      </template>
    </el-page-header>
    <el-container
      v-loading="loading"
      style="min-height: 50vh"
    >
      <div class="form-edit-width-100">
        <common-split
          v-if="projectItem"
          :min-size="150"
          :max-size="[500, Infinity]"
        >
          <template #split-0>
            <api-folder-tree-viewer
              ref="folderTreeRef"
              v-model="projectItem"
              v-model:current-doc="currentDoc"
              :project-item="projectItem"
              editable
            />
          </template>
          <template #split-1>
            <el-container v-if="currentDoc?.editing">
              <markdown-doc-editor
                v-if="currentDoc?.docType==='md'"
                v-model="currentDoc"
                @saved-doc="savedApiDoc"
              />
              <api-doc-viewer
                v-if="currentDoc?.docType==='api'"
                v-model="currentDoc"
                :project-item="projectItem"
              />
            </el-container>
            <el-container v-else>
              <markdown-doc-viewer
                v-if="currentDoc?.docType==='md'"
                v-model="currentDoc"
                editable
                scroll-element=".markdown-doc-viewer .md-editor-preview-wrapper"
              />
              <api-doc-viewer
                v-if="currentDoc?.docType==='api'"
                v-model="currentDoc"
                :project-item="projectItem"
              />
            </el-container>
          </template>
        </common-split>
      </div>
    </el-container>
  </el-container>
</template>

<style scoped>

</style>
