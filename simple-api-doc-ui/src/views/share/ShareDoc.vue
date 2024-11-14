<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { loadProject, loadShare } from '@/api/SimpleShareApi'
import ApiFolderTreeViewer from '@/views/components/api/doc/ApiFolderTreeViewer.vue'
import { $i18nKey } from '@/messages'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import { useTitle } from '@vueuse/core'
import emitter from '@/vendors/emitter'
import ShareDocRightViewer from '@/views/components/api/doc/ShareDocRightViewer.vue'
import ApiDocRequestPreview from '@/views/components/api/ApiDocRequestPreview.vue'
import { useApiDocDebugConfig } from '@/services/api/ApiDocPreviewService'
import { useScreenCheck } from '@/services/api/ApiCommonService'
import { ElMessage } from 'element-plus'

const shareConfigStore = useShareConfigStore()
const route = useRoute()
const shareId = route.params.shareId
const password = route.query.pwd || route.query.password || ''
const shareParam = ref({
  shareId,
  password
})
const loading = ref(false)
const projectShare = ref()
const errorMessage = ref()
const showPassWindow = ref(false)

//= ====================项目数据=====================
const projectItem = ref()
const currentDoc = ref(null)
const errorHandler = (err) => {
  if (err.data?.code === 401) {
    useShareConfigStore().clearShareToken(shareId)
    showPassWindow.value = true
    projectItem.value = null
  }
}
emitter.on('share-doc-error', errorHandler)
emitter.on('preview-401-error', errorHandler)
onUnmounted(() => {
  emitter.off('preview-401-error', errorHandler)
  emitter.off('share-doc-error', errorHandler)
})

const loadShareData = async (input) => {
  loading.value = true
  errorMessage.value = ''
  const param = input ? shareParam.value : { ...shareParam.value, password: shareParam.value.password || shareConfigStore.getShareToken(shareId) }
  projectShare.value = await loadShare(param, {
    showErrorMessage: false
  }).then(data => data.resultData, error => {
    showPassWindow.value = error.data?.code === 401
    errorMessage.value = error.data?.message
    if (shareParam.value.password && errorMessage.value) {
      ElMessage.error(errorMessage.value)
    }
    if (showPassWindow.value) {
      shareConfigStore.clearShareToken(shareId)
    }
    return error.data?.resultData
  }).finally(() => (loading.value = false))
  useTitle(projectShare.value?.shareName)
  console.log('=====================projectShare', projectShare.value)
  if (projectShare.value?.shareToken) { // 验证成功
    showPassWindow.value = false
    shareConfigStore.setShareToken(shareId, projectShare.value.shareToken)
    loadProject(shareId, { loading: true }).then(data => {
      projectItem.value = data.resultData
      console.log('=========================projectItem.value', projectItem.value)
    }).catch(err => {
      errorMessage.value = err.data?.message
      emitter.emit('share-doc-error', err)
    })
  }
}
onMounted(() => loadShareData())
const passwordOptions = [{
  labelKey: 'api.label.accessPassword',
  prop: 'password',
  required: true,
  placeholder: $i18nKey('common.msg.commonInput', 'api.label.accessPassword'),
  attrs: {
    showPassword: true
  }
}]
const toAccessDocs = ({ form }) => {
  submitForm(form)
  return false
}

const submitForm = form => {
  form.validate(valid => {
    if (valid) {
      loadShareData(true)
    }
  })
}

const showDrawerMenu = ref(true)
const { isMobile } = useScreenCheck()
watch(currentDoc, (newDoc, oldDoc) => {
  if (newDoc?.id !== oldDoc?.id) {
    showDrawerMenu.value = false
    hideDebugSplit()
  }
})
const { apiDocPreviewRef, splitSizes, defaultMinSizes, defaultMaxSizes, hideDebugSplit, previewLoading, toDebugApi } = useApiDocDebugConfig()
const splitRef = ref()
</script>

<template>
  <el-container
    :key="$route.fullPath"
    class="flex-column height100"
  >
    <el-affix
      v-if="isMobile||splitRef?.elementSizes?.[0]<50"
      :offset="20"
      style="position: absolute;width: 70px;"
    >
      <el-button
        type="info"
        class="margin-left3"
        @click="showDrawerMenu=!showDrawerMenu"
      >
        <common-icon
          icon="MenuFilled"
          :size="20"
        />
      </el-button>
    </el-affix>
    <el-container
      v-loading="loading"
      class="height100"
    >
      <div
        v-if="!showPassWindow && errorMessage"
        class="form-edit-width-100"
      >
        <el-empty :description="errorMessage" />
      </div>
      <common-window
        v-model="showPassWindow"
        width="500px"
        :show-close="false"
        :show-cancel="false"
        :title="projectShare?.shareName"
        :ok-label="$t('api.label.accessDocs')"
        :ok-click="toAccessDocs"
        :close-on-click-modal="false"
      >
        <el-container class="flex-column">
          <el-alert
            show-icon
            :description="$t('api.msg.docNeedPassword')"
            type="warning"
            :closable="false"
            class="margin-bottom3"
          />
          <common-form
            :options="passwordOptions"
            :model="shareParam"
            :show-submit="false"
            :show-buttons="false"
            @submit-form="submitForm"
          />
        </el-container>
      </common-window>
      <el-container
        v-if="projectItem"
        class="form-edit-width-100 flex-column padding-top3 padding-left3 padding-right3 height100"
      >
        <common-split
          v-if="!isMobile"
          ref="splitRef"
          :sizes="splitSizes"
          :min-size="defaultMinSizes"
          :max-size="defaultMaxSizes"
          class="height100"
        >
          <template #split-0>
            <api-folder-tree-viewer
              v-model="projectItem"
              v-model:current-doc="currentDoc"
              :share-doc="projectShare"
            />
          </template>
          <template #split-1>
            <share-doc-right-viewer
              v-model="currentDoc"
              :project-share="projectShare"
              :project-item="projectItem"
              @to-debug-api="toDebugApi"
            />
          </template>
          <template #split-2>
            <el-container
              class="flex-column padding-left2 height100"
            >
              <el-page-header
                class="padding-bottom2"
                @back="hideDebugSplit"
              >
                <template #content>
                  <span>{{ currentDoc?.docName || currentDoc?.url }} </span>
                </template>
              </el-page-header>
              <api-doc-request-preview
                ref="apiDocPreviewRef"
                v-loading="previewLoading"
                style="min-height:200px;"
                form-height="calc(100vh - 120px)"
              />
            </el-container>
          </template>
        </common-split>
        <el-container
          v-else
          class="flex-column min-width-container"
        >
          <share-doc-right-viewer
            v-model="currentDoc"
            :project-share="projectShare"
            :project-item="projectItem"
          />
        </el-container>
        <el-drawer
          v-model="showDrawerMenu"
          style="min-width: 320px;"
          :with-header="false"
          direction="ltr"
          append-to-body
        >
          <api-folder-tree-viewer
            v-model="projectItem"
            v-model:current-doc="currentDoc"
            :share-doc="projectShare"
          />
        </el-drawer>
      </el-container>
    </el-container>
  </el-container>
</template>

<style scoped>

</style>
