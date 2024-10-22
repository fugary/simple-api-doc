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
import emitter from '@/vendors/emitter'
import { AUTH_TYPE, DEFAULT_PREFERENCE_ID_KEY } from '@/consts/ApiConstants'
import ApiRequestFormAuthorization from '@/views/components/api/form/ApiRequestFormAuthorization.vue'
import { calcAuthModelBySchemas, calcSecuritySchemas } from '@/services/api/ApiDocPreviewService'
import { useScreenCheck } from '@/services/api/ApiCommonService'

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
const getAuthContentModel = () => {
  return {
    ...shareConfigStore.sharePreferenceView[paramTargetId]?.defaultAuthModel || {
      authType: AUTH_TYPE.NONE
    }
  }
}
let lastParamTarget = reactive({})
const securitySchemas = ref()
const supportedAuthTypes = ref()
const authContentModel = ref(getAuthContentModel())
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
  calcSecuritySchemas(projectInfoDetail.value, securitySchemas, supportedAuthTypes)
  calcAuthModelBySchemas(authContentModel.value, securitySchemas.value)
  envConfigs.value = getEnvConfigs(apiDocDetail.value)
  apiDocDetail.value.targetUrl = envConfigs.value[0]?.url
  const calcParamTargetId = `${paramTargetId}-${apiDocDetail.value.id}`
  lastParamTarget = shareConfigStore.shareParamTargets[calcParamTargetId] = shareConfigStore.shareParamTargets[calcParamTargetId] || reactive({})
  lastParamTarget.hasInheritAuth = !!shareConfigStore.sharePreferenceView[paramTargetId].defaultAuthModel
  console.log('======================apiDocDetail', apiDocDetail.value)
}

const handlerConfig = {
  preHandler: target => {
    const savedTarget = { ...lastParamTarget }
    const notSavedKeys = ['requestBodySchema']// 有些数据不能使用保存数据
    notSavedKeys.forEach(key => delete savedTarget[key])
    return Object.assign(target, lastParamTarget)
  },
  changeHandler: target => Object.assign(lastParamTarget, target)
}

watch(apiDoc, loadDocDetail, {
  immediate: true
})
watch(() => authContentModel.value?.authType, () => {
  calcAuthModelBySchemas(authContentModel.value, securitySchemas.value)
})
const theme = computed(() => globalConfigStore.isDarkTheme ? 'dark' : 'light')
const { isMobile } = useScreenCheck()

defineEmits(['toDebugApi'])

const showAuthorizationWindow = ref(false)
const toEditAuthorization = () => {
  authContentModel.value = getAuthContentModel()
  showAuthorizationWindow.value = true
}
const saveAuthorization = ({ form }) => {
  form.validate(valid => {
    if (valid) {
      if (shareConfigStore.sharePreferenceView[paramTargetId]) {
        shareConfigStore.sharePreferenceView[paramTargetId].defaultAuthModel = { ...authContentModel.value }
        lastParamTarget.hasInheritAuth = !!shareConfigStore.sharePreferenceView[paramTargetId].defaultAuthModel
      }
      showAuthorizationWindow.value = false
    }
  })
  return false
}

</script>

<template>
  <el-container
    :key="apiDoc.id"
    v-loading="loading"
    style="height: calc(100% - 45px)"
    class="padding-left2 flex-column padding-right2"
  >
    <api-doc-view-header v-model="apiDoc" />
    <api-doc-path-header
      v-model="apiDocDetail"
      :env-configs="envConfigs"
      :debug-enabled="!isMobile&&(apiDocDetail?.apiShare?.debugEnabled||!shareDoc)"
      :auth-enabled="!isMobile&&!!securitySchemas"
      :auth-configured="lastParamTarget.hasInheritAuth"
      @debug-api="$emit('toDebugApi', projectInfoDetail, apiDocDetail, handlerConfig)"
      @config-auth="toEditAuthorization"
    />
    <el-container
      class="flex-column scroll-main-container"
    >
      <el-scrollbar class="api-doc-viewer">
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
    <el-backtop
      v-common-tooltip="$t('common.label.backtop')"
      target=".api-doc-viewer .el-scrollbar__wrap"
      :right="70"
      :bottom="70"
    />
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
            :disabled="!schema.isSupported"
            :name="schema.authType"
          >
            <template #label>
              <el-text
                v-common-tooltip="!schema.isSupported?$t('api.label.notSupported'):''"
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
          <ApiRequestFormAuthorization
            v-model="authContentModel"
            :form-prop="''"
            :show-auth-types="false"
            :supported-auth-types="supportedAuthTypes"
          />
        </common-form>
      </el-container>
    </common-window>
  </el-container>
</template>

<style scoped>

</style>
