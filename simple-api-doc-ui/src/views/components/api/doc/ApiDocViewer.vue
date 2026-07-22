<script setup>
import { computed, ref, watch, reactive, onMounted, onUnmounted } from 'vue'
import { getEnvConfigs, loadShareDoc } from '@/api/SimpleShareApi'
import { loadDoc } from '@/api/ApiDocApi'
import ApiDocViewHeader from '@/views/components/api/doc/comp/ApiDocViewHeader.vue'
import ApiDocPathHeader from '@/views/components/api/doc/comp/ApiDocPathHeader.vue'
import { MdCatalog, MdPreview } from 'md-editor-v3'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import ApiDocParameters from '@/views/components/api/doc/comp/ApiDocParameters.vue'
import ApiDocRequestBody from '@/views/components/api/doc/comp/ApiDocRequestBody.vue'
import ApiDocResponseBody from '@/views/components/api/doc/comp/ApiDocResponseBody.vue'
import emitter from '@/vendors/emitter'
import { AUTH_TYPE } from '@/consts/ApiConstants'
import ApiDocAuthorizationWindow from '@/views/components/api/doc/comp/ApiDocAuthorizationWindow.vue'
import {
  calcAuthModelBySchemas,
  calcSecuritySchemas,
  copyParamsDynamicOption,
  isGetMethod
} from '@/services/api/ApiDocPreviewService'
import { calcEnvSuggestions, useContainerCheck, useCopyRight, useScreenCheck } from '@/services/api/ApiCommonService'
import { calcPreferenceId, useShareDocTheme } from '@/services/api/ApiFolderService'
import ApiDocSecurityRequirements from '@/views/components/api/doc/comp/ApiDocSecurityRequirements.vue'
import { cloneDeep } from 'lodash-es'

const props = defineProps({
  shareDoc: {
    type: Object,
    default: undefined
  },
  editable: {
    type: Boolean,
    default: false
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

const paramTargetId = calcPreferenceId(props.projectItem, props.shareDoc)
const envSuggestions = computed(() => calcEnvSuggestions(props.projectItem?.groupConfig, paramTargetId))
const sharePreference = shareConfigStore.sharePreferenceView[paramTargetId]
const getAuthContentModel = () => {
  return cloneDeep({
    ...sharePreference?.defaultAuthModel || {
      authType: AUTH_TYPE.NONE,
      authModels: []
    }
  })
}
let lastParamTarget = reactive({})
const securitySchemas = ref()
const supportedAuthTypes = ref()
const authContentModel = ref(getAuthContentModel())
const loading = ref(false)
const historyCount = ref(0)
const viewAsMarkdown = computed(() => sharePreference?.viewAsMarkdown)
const loadDocDetail = async () => {
  if (loading.value) {
    return
  }
  loading.value = true
  if (props.shareDoc?.shareId) {
    const shareId = props.shareDoc.shareId
    apiDocDetail.value = await loadShareDoc({ shareId, docId: apiDoc.value.id, markdown: viewAsMarkdown.value }
      , { showErrorMessage: false })
      .then(data => data.resultData)
      .catch(err => {
        emitter.emit('share-doc-error', err)
        return err
      })
      .finally(() => (loading.value = false))
  } else {
    apiDocDetail.value = await loadDoc(apiDoc.value.id, viewAsMarkdown.value).then(data => {
      historyCount.value = data.addons?.historyCount || 0
      return data.resultData
    }).finally(() => {
      loading.value = false
    })
  }
  projectInfoDetail.value = apiDocDetail.value?.projectInfoDetail
  calcSecuritySchemas(projectInfoDetail.value, apiDocDetail.value, securitySchemas, supportedAuthTypes, props.shareDoc)
  calcAuthModelBySchemas(authContentModel.value, securitySchemas.value)
  if (!sharePreference?.defaultAuthModel && authContentModel.value?.authType && authContentModel.value.authType !== AUTH_TYPE.NONE) {
    saveAuthModel(authContentModel.value)
  }
  envConfigs.value = getEnvConfigs(apiDocDetail.value)
  apiDocDetail.value.targetUrl = envConfigs.value?.find(env => env.url === sharePreference?.targetUrl)?.url || envConfigs.value[0]?.url
  const calcParamTargetId = `${paramTargetId}-${apiDocDetail.value.id}`
  lastParamTarget = shareConfigStore.shareParamTargets[calcParamTargetId] = shareConfigStore.shareParamTargets[calcParamTargetId] || reactive({})
  lastParamTarget.hasInheritAuth = !!sharePreference?.defaultAuthModel
  console.log('======================apiDocDetail', apiDocDetail.value)
}

const handlerConfig = {
  preHandler: target => {
    const savedTarget = { ...lastParamTarget }
    const notSavedKeys = ['requestBodySchema', 'securityRequirements', 'requestExamples', 'groupConfig']// 有些数据不能使用保存数据
    notSavedKeys.forEach(key => delete savedTarget[key])
    copyParamsDynamicOption(target.pathParams, savedTarget.pathParams)
    copyParamsDynamicOption(target.requestParams, savedTarget.requestParams)
    copyParamsDynamicOption(target.headerParams, savedTarget.headerParams)
    return Object.assign(target, savedTarget)
  },
  changeHandler: target => {
    Object.assign(lastParamTarget, target)
    if (apiDocDetail.value?.targetUrl !== target.targetUrl) {
      apiDocDetail.value.targetUrl = lastParamTarget.targetUrl
    }
  }
}

watch(() => apiDocDetail.value?.targetUrl, (targetUrl) => {
  if (sharePreference) {
    sharePreference.targetUrl = targetUrl
  }
})
watch(apiDoc, loadDocDetail, {
  immediate: true
})
watch(() => globalConfigStore.currentLocale, loadDocDetail)
watch(() => authContentModel.value.authKeyName, (authKey) => {
  authContentModel.value.authType = securitySchemas.value?.[authKey]?.authType || AUTH_TYPE.NONE
})
const { isDarkTheme, monacoTheme } = useShareDocTheme(sharePreference)
const theme = computed(() => isDarkTheme.value ? 'dark' : 'light')
const { isMobile } = useScreenCheck()

defineEmits(['toDebugApi', 'updateHistory', 'toEditSecuritySchemas'])

const showAuthorizationWindow = ref(false)
const toEditAuthorization = () => {
  if (sharePreference.defaultAuthModel) {
    authContentModel.value = getAuthContentModel()
    calcAuthModelBySchemas(authContentModel.value, securitySchemas.value)
  }
  showAuthorizationWindow.value = true
}

const handleOpenAuthWindow = (docId) => {
  if (apiDocDetail.value?.id === docId) {
    toEditAuthorization()
  }
}
onMounted(() => {
  emitter.on('open-authorization-window', handleOpenAuthWindow)
})
onUnmounted(() => {
  emitter.off('open-authorization-window', handleOpenAuthWindow)
})

const saveAuthModel = (model) => {
  if (sharePreference) {
    sharePreference.defaultAuthModel = model ? cloneDeep(model) : undefined
    lastParamTarget.hasInheritAuth = !!sharePreference.defaultAuthModel
  }
}
const saveAuthorization = ({ form }) => {
  form.validate(valid => {
    if (valid) {
      saveAuthModel(authContentModel.value)
      showAuthorizationWindow.value = false
    }
  })
  return false
}

const clearAuthorization = () => {
  calcAuthModelBySchemas(authContentModel.value = {
    authType: AUTH_TYPE.NONE,
    authModels: []
  }, securitySchemas.value)
  saveAuthModel()
  showAuthorizationWindow.value = false
}

const supportedAuthModels = computed(() => {
  return authContentModel.value?.authModels?.filter(authModel => authModel.isSupported) || []
})
const copyRight = useCopyRight(props.shareDoc)
const docContent = computed(() => {
  return apiDocDetail.value?.docContent || apiDocDetail.value?.description
})
const { isSmallContainer, containerRef } = useContainerCheck()
</script>

<template>
  <el-container
    :key="apiDoc.id"
    v-loading="loading"
    style="height: 100%"
    class="padding-left2 flex-column padding-right2"
  >
    <api-doc-view-header
      v-model="apiDoc"
      :current-doc-detail="apiDocDetail"
      :editable="editable"
      :history-count="historyCount"
      @update-history="$emit('updateHistory', $event)"
    />
    <api-doc-path-header
      v-if="apiDocDetail"
      v-model="apiDocDetail"
      v-model:view-as-markdown="sharePreference.viewAsMarkdown"
      :env-configs="envConfigs"
      :debug-enabled="!isMobile&&(apiDocDetail?.apiShare?.debugEnabled||!shareDoc)"
      view-as-enabled
      :auth-enabled="!isMobile&&!!supportedAuthModels?.length"
      :auth-configured="lastParamTarget.hasInheritAuth"
      @update:view-as-markdown="loadDocDetail"
      @debug-api="$emit('toDebugApi', projectInfoDetail, apiDocDetail, handlerConfig)"
      @config-auth="toEditAuthorization"
    />
    <el-container
      v-if="apiDocDetail"
      class="flex-column scroll-main-container"
    >
      <el-scrollbar
        v-if="!viewAsMarkdown"
        class="api-doc-viewer"
      >
        <h3 v-if="docContent">
          {{ $t('api.label.apiDescription') }}
        </h3>
        <md-preview
          v-if="docContent"
          class="md-doc-container"
          :theme="theme"
          :model-value="docContent"
        />
        <api-doc-security-requirements
          v-if="!shareDoc&&supportedAuthModels.length||editable"
          v-model="apiDocDetail"
          :editable="editable"
          @to-edit-security-schemas="$emit('toEditSecuritySchemas')"
          @schema-updated="loadDocDetail"
        />
        <api-doc-parameters
          v-if="apiDocDetail?.parametersSchema||!apiDocDetail?.requestsSchemas?.length||editable"
          v-model="apiDocDetail"
          :editable="editable"
          @schema-updated="loadDocDetail"
        />
        <api-doc-request-body
          v-if="apiDocDetail?.requestsSchemas?.length||(editable&&!isGetMethod(apiDocDetail.method))"
          v-model="apiDocDetail"
          :theme="theme"
          :monaco-theme="monacoTheme"
          :editable="editable"
          @schema-updated="loadDocDetail"
        />
        <api-doc-response-body
          v-if="apiDocDetail?.responsesSchemas?.length||editable"
          v-model="apiDocDetail"
          :theme="theme"
          :monaco-theme="monacoTheme"
          :editable="editable"
          @schema-updated="loadDocDetail"
        />
      </el-scrollbar>
      <el-container
        v-else
        ref="containerRef"
        class="scroll-main-container"
      >
        <md-preview
          class="md-doc-container"
          editor-id="api-doc-preview-only"
          :theme="theme"
          :model-value="apiDocDetail.apiMarkdown"
        />
        <el-scrollbar
          v-if="!isSmallContainer"
          class="md-doc-catalog"
        >
          <md-catalog
            class="md-catalog"
            editor-id="api-doc-preview-only"
          />
        </el-scrollbar>
      </el-container>
    </el-container>
    <el-container
      class="text-center padding-10 padding-bottom3 flex-center"
      style="flex-grow: 0"
    >
      <span>
        <el-text><copy-right /></el-text>
      </span>
    </el-container>
    <template v-if="apiDocDetail">
      <el-backtop
        v-if="viewAsMarkdown"
        v-common-tooltip="$t('common.label.backtop')"
        target=".md-editor-preview-wrapper"
        :right="40"
        :bottom="40"
      />
      <el-backtop
        v-else
        v-common-tooltip="$t('common.label.backtop')"
        target=".api-doc-viewer .el-scrollbar__wrap"
        :right="40"
        :bottom="40"
      />
    </template>
    <ApiDocAuthorizationWindow
      v-model="showAuthorizationWindow"
      v-model:auth-content-model="authContentModel"
      :security-schemas="securitySchemas"
      :group-config="props.projectItem?.groupConfig"
      :preference-id="paramTargetId"
      :env-suggestions="envSuggestions"
      :ok-click="saveAuthorization"
      :clear-click="clearAuthorization"
    />
  </el-container>
</template>

<style scoped>
.api-doc-viewer :deep(h3) {
  margin-block-start: 8px;
  margin-block-end: 8px;
}
.api-doc-viewer :deep(.md-editor-preview-wrapper) {
  padding: 5px 0;
}
.api-doc-viewer :deep(.md-editor) {
  --md-bk-color: transparent;
}
</style>
