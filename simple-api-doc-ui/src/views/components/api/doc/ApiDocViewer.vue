<script setup>
import { computed, ref, watch, reactive } from 'vue'
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
import ApiRequestFormAuthorization from '@/views/components/api/form/ApiRequestFormAuthorization.vue'
import { calcAuthModelBySchemas, calcSecuritySchemas, copyParamsDynamicOption } from '@/services/api/ApiDocPreviewService'
import { useContainerCheck, useCopyRight, useScreenCheck } from '@/services/api/ApiCommonService'
import { calcPreferenceId } from '@/services/api/ApiFolderService'
import { $i18nBundle } from '@/messages'

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
const sharePreference = shareConfigStore.sharePreferenceView[paramTargetId]
const getAuthContentModel = () => {
  return {
    ...sharePreference?.defaultAuthModel || {
      authType: AUTH_TYPE.NONE,
      authModels: []
    }
  }
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
    apiDocDetail.value = await loadShareDoc({ shareId, docId: apiDoc.value.id, markdown: viewAsMarkdown.value })
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
  calcSecuritySchemas(projectInfoDetail.value, securitySchemas, supportedAuthTypes)
  calcAuthModelBySchemas(apiDocDetail.value, authContentModel.value, securitySchemas.value)
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
    const notSavedKeys = ['requestBodySchema', 'securityRequirements']// 有些数据不能使用保存数据
    notSavedKeys.forEach(key => delete savedTarget[key])
    copyParamsDynamicOption(target.pathParams, savedTarget.pathParams)
    copyParamsDynamicOption(target.requestParams, savedTarget.requestParams)
    copyParamsDynamicOption(target.headerParams, savedTarget.headerParams)
    return Object.assign(target, lastParamTarget)
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
const theme = computed(() => globalConfigStore.isDarkTheme ? 'dark' : 'light')
const { isMobile } = useScreenCheck()

defineEmits(['toDebugApi'])

const showAuthorizationWindow = ref(false)
const toEditAuthorization = () => {
  showAuthorizationWindow.value = true
}
const saveAuthorization = ({ form }) => {
  form.validate(valid => {
    if (valid) {
      if (sharePreference) {
        sharePreference.defaultAuthModel = { ...authContentModel.value }
        lastParamTarget.hasInheritAuth = !!sharePreference.defaultAuthModel
      }
      showAuthorizationWindow.value = false
    }
  })
  return false
}

const getNotSupportedMsg = (schema) => {
  if (!schema.isSupported) {
    return $i18nBundle('api.label.notSupported')
  }
  if (!schema.authModel?.isSupported) {
    return $i18nBundle('api.msg.authNotSupported')
  }
}

const supportedAuthModels = computed(() => {
  console.log('===================================authContentModel', authContentModel.value)
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
      v-model:history-count="historyCount"
      :current-doc-detail="apiDocDetail"
      :editable="editable"
    />
    <api-doc-path-header
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
        <api-doc-parameters
          v-if="apiDocDetail?.projectInfoDetail&&apiDocDetail?.parametersSchema||!apiDocDetail?.requestsSchemas?.length"
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
        :right="70"
        :bottom="70"
      />
      <el-backtop
        v-else
        v-common-tooltip="$t('common.label.backtop')"
        target=".api-doc-viewer .el-scrollbar__wrap"
        :right="70"
        :bottom="70"
      />
    </template>
    <common-window
      v-model="showAuthorizationWindow"
      :title="$t('api.label.authorization')"
      :ok-click="saveAuthorization"
    >
      <el-container class="flex-column">
        <el-tabs v-model="authContentModel.authType">
          <el-tab-pane
            v-for="(schema, name) in securitySchemas"
            :key="name"
            :disabled="!schema.isSupported||!schema.authModel?.isSupported"
            :name="schema.authType"
          >
            <template #label>
              <el-text
                v-common-tooltip="getNotSupportedMsg(schema)"
                type="info"
              >
                {{ name }}
                <span v-if="schema.type">
                  ({{ schema.type }})
                </span>
              </el-text>
            </template>
            <div
              v-if="schema.description"
              class="padding-top2 padding-bottom3"
            >
              <el-text>
                {{ schema.description }}
              </el-text>
            </div>
          </el-tab-pane>
        </el-tabs>
        <common-form
          :model="authContentModel"
          :show-buttons="false"
        >
          <template
            v-for="(authModel, index) in supportedAuthModels"
            :key="authModel.authType"
          >
            <ApiRequestFormAuthorization
              v-if="authContentModel.authType===authModel.authType"
              :model-value="authModel"
              :form-prop="`authModels[${index}]`"
              :show-auth-types="false"
              :supported-auth-types="[authModel.authType]"
            />
          </template>
        </common-form>
      </el-container>
    </common-window>
  </el-container>
</template>

<style scoped>

</style>
