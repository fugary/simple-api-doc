<script setup>
import { computed, ref, watch, reactive } from 'vue'
import { getEnvConfigs, loadShareDoc } from '@/api/SimpleShareApi'
import { loadDoc } from '@/api/ApiDocApi'
import ApiDocViewHeader from '@/views/components/api/doc/comp/ApiDocViewHeader.vue'
import ApiDocPathHeader from '@/views/components/api/doc/comp/ApiDocPathHeader.vue'
import { MdPreview } from 'md-editor-v3'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import ApiDocParameters from '@/views/components/api/doc/comp/ApiDocParameters.vue'
import ApiDocRequestBody from '@/views/components/api/doc/comp/ApiDocRequestBody.vue'
import ApiDocResponseBody from '@/views/components/api/doc/comp/ApiDocResponseBody.vue'
import { useFolderLayoutHeight } from '@/services/api/ApiFolderService'
import { previewApiRequest } from '@/utils/DynamicUtils'
import { useWindowSize } from '@vueuse/core'
import emitter from '@/vendors/emitter'
import { DEFAULT_PREFERENCE_ID_KEY } from '@/consts/ApiConstants'

const props = defineProps({
  shareDoc: {
    type: Object,
    default: undefined
  },
  projectItem: {
    type: Object,
    default: undefined
  }
})

const apiDoc = defineModel({
  type: Object
})

const shareConfigStore = useShareConfigStore()
const globalConfigStore = useGlobalConfigStore()

const apiDocDetail = ref()
const projectInfoDetail = ref()
const envConfigs = ref([])

const paramTargetId = props.shareDoc?.shareId || props.projectItem?.projectCode || DEFAULT_PREFERENCE_ID_KEY

let lastParamTarget = reactive({})
const loading = ref(false)
const loadDocDetail = async () => {
  if (loading.value) {
    return
  }
  loading.value = true
  if (props.shareDoc?.shareId) {
    const shareId = props.shareDoc.shareId
    apiDocDetail.value = await loadShareDoc({ shareId, docId: apiDoc.value.id })
      .then(data => data.resultData)
      .catch(err => {
        emitter.emit('share-doc-error', err)
        return err
      })
      .finally(() => (loading.value = false))
  } else {
    apiDocDetail.value = await loadDoc(apiDoc.value.id).then(data => data.resultData).finally(() => {
      loading.value = false
    })
  }
  projectInfoDetail.value = apiDocDetail.value?.projectInfoDetail
  envConfigs.value = getEnvConfigs(apiDocDetail.value)
  apiDocDetail.value.targetUrl = envConfigs.value[0]?.url
  const calcParamTargetId = `${paramTargetId}-${apiDocDetail.value.id}`
  lastParamTarget = shareConfigStore.shareParamTargets[calcParamTargetId] = shareConfigStore.shareParamTargets[calcParamTargetId] || reactive({})
  console.log('======================apiDocDetail', apiDocDetail.value)
}

const handlerConfig = {
  preHandler: target => Object.assign(target, lastParamTarget),
  changeHandler: target => Object.assign(lastParamTarget, target)
}

watch(apiDoc, loadDocDetail, {
  immediate: true
})
const theme = computed(() => globalConfigStore.isDarkTheme ? 'dark' : 'light')
const folderContainerHeight = useFolderLayoutHeight(!props.shareDoc, props.shareDoc ? -70 : -40)
const { width } = useWindowSize()
const isMobile = computed(() => width.value <= 768)

const emit = defineEmits(['toPreviewApi'])
const toDebugApi = () => {
  previewApiRequest(projectInfoDetail.value, apiDocDetail.value, handlerConfig)
  emit('toPreviewApi', projectInfoDetail.value, apiDocDetail.value, handlerConfig)
}
</script>

<template>
  <el-container
    :key="apiDoc.id"
    v-loading="loading"
    class="padding-left2 flex-column padding-right2"
    style="min-height: 50vh"
  >
    <api-doc-view-header v-model="apiDoc" />
    <api-doc-path-header
      v-model="apiDocDetail"
      :env-configs="envConfigs"
      :debug-enabled="!isMobile&&(apiDocDetail?.apiShare?.debugEnabled||!shareDoc)"
      @debug-api="toDebugApi"
    />
    <el-container
      :style="{height:folderContainerHeight}"
      class="flex-column"
    >
      <el-scrollbar>
        <h3 v-if="apiDocDetail?.description">
          {{ $t('api.label.apiDescription') }}
        </h3>
        <md-preview
          v-if="apiDocDetail?.description"
          :theme="theme"
          :model-value="apiDocDetail?.description"
        />
        <api-doc-parameters
          v-if="!apiDocDetail?.requestsSchemas?.length"
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
