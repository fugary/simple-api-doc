<script setup lang="jsx">
import { useRoute } from 'vue-router'
import { $coreShowLoading, $coreHideLoading, $goto, calcAffixOffset, useBackUrl } from '@/utils'
import { ref, watch, computed, onActivated, onMounted } from 'vue'
import { useApiProjectItem } from '@/api/ApiProjectApi'
import MarkdownDocViewer from '@/views/components/api/doc/MarkdownDocViewer.vue'
import ApiDocViewer from '@/views/components/api/doc/ApiDocViewer.vue'
import ApiFolderTreeViewer from '@/views/components/api/doc/ApiFolderTreeViewer.vue'
import MarkdownDocEditor from '@/views/components/api/doc/MarkdownDocEditor.vue'
import ApiDocEditor from '@/views/components/api/doc/ApiDocEditor.vue'
import { useApiDocDebugConfig } from '@/services/api/ApiDocPreviewService'
import ApiDocRequestPreview from '@/views/components/api/ApiDocRequestPreview.vue'
import { AUTHORITY_TYPE } from '@/consts/ApiConstants'
import { inProjectCheckAccess } from '@/api/ApiProjectGroupApi'
import { useInitLoadOnce } from '@/hooks/CommonHooks'
import { toEditEnvConfigs } from '@/utils/DynamicUtils'
import { $i18nKey } from '@/messages'

const route = useRoute()
const projectCode = route.params.projectCode

const defaultProjectUrl = '/api/projects'
const { goBack, backUrl } = useBackUrl('/api/projects')
const { projectItem, loading, loadProjectItem } = useApiProjectItem(projectCode, { autoLoad: false })

const folderTreeRef = ref()
const currentDoc = ref(null)
const savedApiDoc = (newDoc) => {
  if (folderTreeRef.value?.sharePreference) {
    folderTreeRef.value.sharePreference.lastDocId = newDoc.id
  } else {
    currentDoc.value = newDoc
  }
  folderTreeRef.value?.refreshProjectItem()
}
const lastDocInfo = ref()
watch(currentDoc, (newDoc, oldDoc) => {
  if (newDoc?.id !== oldDoc?.id) {
    hideDebugSplit()
    lastDocInfo.value = oldDoc
  }
})
watch(() => currentDoc.value?.editing, (newEditing, oldEditing) => {
  if (!currentDoc.value?.id && !newEditing && oldEditing && lastDocInfo.value) {
    currentDoc.value = lastDocInfo.value
  }
})
const { apiDocPreviewRef, splitSizes, defaultMinSizes, defaultMaxSizes, hideDebugSplit, previewLoading, toDebugApi, changeForceShowWindow } = useApiDocDebugConfig(true)
const folderContainerHeight = computed(() => {
  const offset = calcAffixOffset()
  return `calc(100vh - ${170 + offset}px)`
})
watch(loading, (newLoading) => {
  newLoading ? $coreShowLoading({ delay: 0, target: '.home-main' }) : $coreHideLoading()
}, { immediate: true })

const { initLoadOnce } = useInitLoadOnce(() => loadProjectItem(projectCode))
onMounted(initLoadOnce)
onActivated(initLoadOnce)

const isDeletable = computed(() => inProjectCheckAccess(projectItem.value, AUTHORITY_TYPE.DELETABLE))
const isWritable = computed(() => inProjectCheckAccess(projectItem.value, AUTHORITY_TYPE.WRITABLE) || isDeletable.value)

const envConfigs = computed(() => {
  return JSON.parse(projectItem.value?.envContent || '[]') || []
})

</script>

<template>
  <el-container class="flex-column height100">
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
          <el-link
            v-if="defaultProjectUrl!==backUrl"
            type="primary"
            class="margin-right3"
            @click="$goto(defaultProjectUrl)"
          >
            {{ $i18nKey('common.label.commonBack', 'api.label.apiProjects') }}
          </el-link>
          <el-button
            @click="goBack"
          >
            {{ $t('common.label.back') }}
          </el-button>
          <el-button
            class="margin-left2"
            type="warning"
            @click="$goto(`/api/projects/components/${projectItem.projectCode}?backUrl=${route.fullPath}`)"
          >
            <common-icon
              icon="SettingsInputComponentOutlined"
              class="margin-right1"
            />
            {{ $t('api.label.dataModel') }}
          </el-button>
          <el-badge
            v-if="isWritable"
            :value="envConfigs.length"
            :show-zero="false"
            type="primary"
            class="padding-left2"
          >
            <el-button
              type="info"
              @click="toEditEnvConfigs(projectItem).then(() => savedApiDoc(currentDoc))"
            >
              {{ $t('api.label.environments') }}
            </el-button>
          </el-badge>
          <el-badge
            v-if="isWritable"
            :value="projectItem.shares?.filter(share=>share.status).length"
            :show-zero="false"
            type="primary"
            class="padding-left2"
          >
            <el-button
              type="primary"
              @click="$goto(`/api/projects/shares/${projectItem.projectCode}?backUrl=${route.fullPath}`)"
            >
              <common-icon
                icon="Share"
                class="margin-right1"
              />
              {{ $t('api.label.shareDocs') }}
            </el-button>
          </el-badge>
          <el-badge
            v-if="isWritable"
            :value="projectItem.tasks?.filter(task=>task.status).length"
            :show-zero="false"
            type="success"
            class="padding-left2"
          >
            <el-button
              type="success"
              @click="$goto(`/api/projects/tasks/${projectItem.projectCode}?backUrl=${route.fullPath}`)"
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
      style="height: calc(100% - 60px);"
    >
      <div class="form-edit-width-100">
        <common-split
          v-if="projectItem"
          :sizes="splitSizes"
          :min-size="defaultMinSizes"
          :max-size="defaultMaxSizes"
          class="height100"
        >
          <template #split-0>
            <api-folder-tree-viewer
              ref="folderTreeRef"
              v-model="projectItem"
              v-model:current-doc="currentDoc"
              :project-item="projectItem"
              :editable="isWritable"
              :deletable="isDeletable"
            />
          </template>
          <template #split-1>
            <el-container
              v-if="currentDoc?.editing"
              class="height100"
            >
              <markdown-doc-editor
                v-if="currentDoc?.docType==='md'"
                v-model="currentDoc"
                @saved-doc="savedApiDoc"
              />
              <api-doc-editor
                v-if="currentDoc?.docType==='api'&&currentDoc.editing"
                v-model="currentDoc"
                @saved-doc="savedApiDoc"
              />
            </el-container>
            <el-container
              v-else
              class="flex-column height100"
            >
              <markdown-doc-viewer
                v-if="currentDoc?.docType==='md'"
                v-model="currentDoc"
                :project-item="projectItem"
                :editable="isWritable"
                scroll-element=".markdown-doc-viewer .md-editor-preview-wrapper"
                @update-history="savedApiDoc"
              />
              <api-doc-viewer
                v-if="currentDoc?.docType==='api'"
                v-model="currentDoc"
                :project-item="projectItem"
                :editable="isWritable"
                @to-debug-api="toDebugApi"
                @update-history="savedApiDoc"
              />
            </el-container>
          </template>
          <template #split-2>
            <el-container class="flex-column padding-left2">
              <el-page-header
                class="padding-bottom2 padding-top2"
                @back="hideDebugSplit"
              >
                <template #content>
                  <span>{{ currentDoc?.docName || currentDoc?.url }} </span>
                  <el-link
                    v-common-tooltip="$t('api.label.debugInModalWindow')"
                    type="primary"
                    underline="never"
                    class="margin-left1"
                    @click="changeForceShowWindow"
                  >
                    <common-icon
                      size="18"
                      icon="OpenInNewFilled"
                    />
                  </el-link>
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
