<script setup>
import { onMounted, ref, computed, watch, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { loadProject, loadShare } from '@/api/SimpleShareApi'
import ApiDocViewer from '@/views/components/api/doc/ApiDocViewer.vue'
import ApiFolderTreeViewer from '@/views/components/api/doc/ApiFolderTreeViewer.vue'
import MarkdownDocViewer from '@/views/components/api/doc/MarkdownDocViewer.vue'
import { $i18nKey } from '@/messages'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import { APP_VERSION } from '@/config'
import { useWindowSize } from '@vueuse/core'
import emitter from '@/vendors/emitter'

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
  projectShare.value = await loadShare(param).then(data => data.resultData, error => {
    showPassWindow.value = error.data?.code === 401
    errorMessage.value = error.data?.message
    if (showPassWindow.value) {
      shareConfigStore.clearShareToken(shareId)
    }
    return error.data?.resultData
  }).finally(() => (loading.value = false))
  console.log('=====================projectShare', projectShare.value)
  if (projectShare.value?.shareToken) { // 验证成功
    showPassWindow.value = false
    shareConfigStore.setShareToken(shareId, projectShare.value.shareToken)
    loadProject(shareId, { loading: true }).then(data => {
      projectItem.value = data.resultData
      console.log('=========================projectItem.value', projectItem.value)
    }).catch(err => emitter.emit('share-doc-error', err))
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

const { width } = useWindowSize()
const showDrawerMenu = ref(true)
const isMobile = computed(() => width.value <= 768)
watch(currentDoc, () => {
  showDrawerMenu.value = false
})
</script>

<template>
  <el-container class="flex-column">
    <el-affix
      v-if="isMobile"
      :offset="20"
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
      style="min-height: 50vh"
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
            :title="projectShare?.shareName"
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
        class="form-edit-width-100 flex-column padding-top3 padding-left3 padding-right3"
      >
        <common-split
          v-if="!isMobile"
          :min-size="150"
          :max-size="[500, Infinity]"
        >
          <template #split-0>
            <api-folder-tree-viewer
              v-model="projectItem"
              v-model:current-doc="currentDoc"
              :share-doc="projectShare"
            />
          </template>
          <template #split-1>
            <markdown-doc-viewer
              v-if="currentDoc?.docType==='md'"
              v-model="currentDoc"
              scroll-element=".markdown-doc-viewer .md-editor-preview-wrapper"
            />
            <api-doc-viewer
              v-if="currentDoc?.docType==='api'"
              v-model="currentDoc"
              :share-doc="projectShare"
              :project-item="projectItem"
            />
            <el-container class="text-center padding-10 flex-center">
              <span>
                <el-text>Copyright © 2024 Version: {{ APP_VERSION }}</el-text>
              </span>
            </el-container>
          </template>
        </common-split>
        <el-container
          v-else
          class="flex-column min-width-container"
        >
          <el-drawer
            v-show="!!currentDoc"
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
          <markdown-doc-viewer
            v-if="currentDoc?.docType==='md'"
            v-model="currentDoc"
            scroll-element=".markdown-doc-viewer .md-editor-preview-wrapper"
          />
          <api-doc-viewer
            v-if="currentDoc?.docType==='api'"
            v-model="currentDoc"
            :share-doc="projectShare"
            :project-item="projectItem"
          />
          <el-container class="text-center padding-10 flex-center">
            <span>
              <el-text>Copyright © 2024 Version: {{ APP_VERSION }}</el-text>
            </span>
          </el-container>
        </el-container>
      </el-container>
    </el-container>
  </el-container>
</template>

<style scoped>

</style>
