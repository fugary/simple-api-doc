<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { loadProject, loadShare } from '@/api/SimpleShareApi'
import ApiDocViewer from '@/views/components/api/doc/ApiDocViewer.vue'
import ApiFolderTreeViewer from '@/views/components/api/doc/ApiFolderTreeViewer.vue'
import MarkdownDocViewer from '@/views/components/api/doc/MarkdownDocViewer.vue'
import { $i18nKey } from '@/messages'
import { useShareConfigStore } from '@/stores/ShareConfigStore'

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
    }).catch(err => {
      if (err.data?.code === 401) {
        useShareConfigStore().clearShareToken(shareId)
        showPassWindow.value = true
      }
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
  form.validate(valid => {
    if (valid) {
      loadShareData(true)
    }
  })
  return false
}
</script>

<template>
  <el-container class="flex-column">
    <el-container
      v-loading="loading"
      style="min-height: 100vh"
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
      >
        <el-container class="flex-column">
          <el-alert
            show-icon
            :title="projectShare?.shareName"
            description="当前文档需要密码才可访问"
            type="warning"
            :closable="false"
            class="margin-bottom3"
          />
          <common-form
            :options="passwordOptions"
            :model="shareParam"
            :show-submit="false"
            :show-buttons="false"
          />
        </el-container>
      </common-window>
      <el-container
        v-if="projectItem"
        class="form-edit-width-100 flex-column padding-15"
      >
        <common-split
          v-if="projectItem"
          :min-size="150"
          :max-size="[500, Infinity]"
        >
          <template #split-0>
            <api-folder-tree-viewer
              v-model="currentDoc"
              :project-item="projectItem"
              :share-doc="projectShare"
            />
          </template>
          <template #split-1>
            <markdown-doc-viewer
              v-if="currentDoc?.docType==='md'&&currentDoc?.docContent"
              v-model="currentDoc"
            />
            <api-doc-viewer
              v-if="currentDoc?.docType==='api'"
              v-model="currentDoc"
              :share-id="projectShare?.shareId"
            />
          </template>
        </common-split>
      </el-container>
    </el-container>
  </el-container>
</template>

<style scoped>

</style>
