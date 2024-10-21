<script setup lang="jsx">
import { useRoute } from 'vue-router'
import { $goto, useBackUrl } from '@/utils'
import { ref, watch } from 'vue'
import { useApiProjectItem } from '@/api/ApiProjectApi'
import MarkdownDocViewer from '@/views/components/api/doc/MarkdownDocViewer.vue'
import ApiDocViewer from '@/views/components/api/doc/ApiDocViewer.vue'
import ApiFolderTreeViewer from '@/views/components/api/doc/ApiFolderTreeViewer.vue'
import MarkdownDocEditor from '@/views/components/api/doc/MarkdownDocEditor.vue'
import { APP_VERSION } from '@/config'
import { useApiDocDebugConfig } from '@/services/api/ApiDocPreviewService'
import ApiDocRequestPreview from '@/views/components/api/ApiDocRequestPreview.vue'
import { useFolderLayoutHeight } from '@/services/api/ApiFolderService'
import { useScreenCheck } from '@/services/api/ApiCommonService'

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

watch(currentDoc, () => {
  if (currentDoc.value?.docType === 'md') {
    hideDebugSplit()
  }
})
const { isMediumScreen } = useScreenCheck()
const { apiDocPreviewRef, splitSizes, defaultMinSizes, defaultMaxSizes, hideDebugSplit, previewLoading, toDebugApi } = useApiDocDebugConfig(isMediumScreen)
const folderContainerHeight = useFolderLayoutHeight(true, 20)
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
          :sizes="splitSizes"
          :min-size="defaultMinSizes"
          :max-size="defaultMaxSizes"
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
            <el-container
              v-else
              class="flex-column"
            >
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
                @to-debug-api="toDebugApi"
              />
              <el-container class="text-center padding-10 flex-center">
                <span>
                  <el-text>Copyright © 2024 Version: {{ APP_VERSION }}</el-text>
                </span>
              </el-container>
            </el-container>
          </template>
          <template #split-2>
            <el-container class="flex-column padding-left2">
              <el-page-header
                class="padding-bottom2"
                @back="hideDebugSplit"
              >
                <template #content>
                  <span>{{ currentDoc?.docName }} </span>
                </template>
              </el-page-header>
              <api-doc-request-preview
                ref="apiDocPreviewRef"
                v-loading="previewLoading"
                style="min-height:200px;"
                :form-height="folderContainerHeight"
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
