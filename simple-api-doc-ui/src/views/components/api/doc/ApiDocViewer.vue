<script setup>
import { computed, ref, watch } from 'vue'
import { loadShareDoc } from '@/api/SimpleShareApi'
import { loadDoc } from '@/api/ApiDocApi'
import ApiDocViewHeader from '@/views/components/api/doc/comp/ApiDocViewHeader.vue'
import ApiDocPathHeader from '@/views/components/api/doc/comp/ApiDocPathHeader.vue'
import { MdPreview } from 'md-editor-v3'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import ApiDocParameters from '@/views/components/api/doc/comp/ApiDocParameters.vue'
import ApiDocRequestBody from '@/views/components/api/doc/comp/ApiDocRequestBody.vue'
import ApiDocResponseBody from '@/views/components/api/doc/comp/ApiDocResponseBody.vue'
import { useFolderLayoutHeight } from '@/services/api/ApiFolderService'

const props = defineProps({
  shareId: {
    type: String,
    default: ''
  }
})

const apiDoc = defineModel({
  type: Object
})

const apiDocDetail = ref()
const projectInfoDetail = ref()
const envConfigs = ref([])
const docParam = ref({
  baseUrl: ''
})

const loadDocDetail = async () => {
  if (props.shareId) {
    apiDocDetail.value = await loadShareDoc({ shareId: props.shareId, docId: apiDoc.value.id }, {
      loading: true
    }).then(data => data.resultData)
  } else {
    apiDocDetail.value = await loadDoc(apiDoc.value.id, {
      loading: true
    }).then(data => data.resultData)
  }
  projectInfoDetail.value = apiDocDetail.value?.projectInfoDetail
  if (projectInfoDetail.value?.envContent) {
    envConfigs.value = JSON.parse(projectInfoDetail.value?.envContent) || []
    docParam.value.baseUrl = envConfigs.value[0]?.url
  }
  console.log('====================================apiDocDetail', apiDocDetail.value, apiDoc.value)
}

watch(apiDoc, loadDocDetail)
const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')
const folderContainerHeight = useFolderLayoutHeight(!props.shareId, props.shareId ? -70 : -40)
</script>

<template>
  <el-container
    :key="apiDoc.id"
    class="padding-left2 flex-column padding-right2"
  >
    <api-doc-view-header v-model="apiDoc" />
    <api-doc-path-header
      v-model="docParam"
      :api-doc-detail="apiDocDetail"
      :env-configs="envConfigs"
    />
    <el-container
      :style="{height:folderContainerHeight}"
      class="flex-column"
    >
      <el-scrollbar>
        <h3 v-if="apiDocDetail?.description">
          接口描述
        </h3>
        <md-preview
          v-if="apiDocDetail?.description"
          :theme="theme"
          :model-value="apiDocDetail?.description"
        />
        <api-doc-parameters
          v-if="apiDocDetail?.parametersSchema || !apiDocDetail?.requestsSchemas?.length"
          v-model="apiDocDetail"
        />
        <api-doc-request-body
          v-if="apiDocDetail?.requestsSchemas?.length"
          v-model="apiDocDetail"
        />
        <api-doc-response-body
          v-if="apiDocDetail?.responsesSchemas?.length"
          v-model="apiDocDetail"
        />
      </el-scrollbar>
    </el-container>
  </el-container>
</template>

<style scoped>

</style>
