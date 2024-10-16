<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import {
  calcParamTarget,
  calcRequestBody,
  preProcessParams,
  previewRequest,
  processResponse
} from '@/services/api/ApiDocPreviewService'
import ApiRequestForm from '@/views/components/api/form/ApiRequestForm.vue'
import { AUTH_OPTION_CONFIG } from '@/services/api/ApiAuthorizationService'
import { processEvnParams } from '@/services/api/ApiCommonService'
import { joinPath } from '@/utils'
import {
  AUTH_TYPE,
  DEFAULT_PREFERENCE_ID_KEY,
  SIMPLE_API_ACCESS_TOKEN_HEADER,
  SIMPLE_API_TARGET_URL_HEADER
} from '@/consts/ApiConstants'
import { useLoginConfigStore } from '@/stores/LoginConfigStore'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import emitter from '@/vendors/emitter'
import { getEnvConfigs } from '@/api/SimpleShareApi'
import { isFunction } from 'lodash-es'

const projectInfoDetail = ref()
const apiDocDetail = ref()
const envConfigs = ref([])
const paramTarget = ref()
const responseTarget = ref()
const schemas = ref([])

let handlerConfig = {}
const toPreviewRequest = async (projectInfo, apiDoc, handConfig) => {
  projectInfoDetail.value = projectInfo
  apiDocDetail.value = apiDoc
  handlerConfig = handConfig
  clearParamsAndResponse()
  return nextTick(() => {
    const target = calcParamTarget(projectInfoDetail.value, apiDocDetail.value)
    paramTarget.value = isFunction(handlerConfig.preHandler) ? handlerConfig.preHandler(target) : target
    envConfigs.value = getEnvConfigs(apiDocDetail.value)
    paramTarget.value.targetUrl = apiDocDetail.value.targetUrl || envConfigs.value[0]?.url
  })
}
watch(paramTarget, () => {
  if (isFunction(handlerConfig.changeHandler) && paramTarget.value) {
    handlerConfig.changeHandler(paramTarget.value)
  }
}, { deep: true })

const requestPath = computed(() => {
  if (apiDocDetail.value) {
    return apiDocDetail.value.url
  }
  return ''
})

const doDataPreview = async () => {
  console.log('========================paramTarget1', paramTarget.value, apiDocDetail.value)
  const serverSend = paramTarget.value?.sendType === 'server'
  let processedPath = requestPath.value
  paramTarget.value?.pathParams?.forEach(pathParam => {
    const pathValue = processEvnParams(paramTarget.value.groupConfig, pathParam.value)
    if (pathValue) {
      processedPath = processedPath.replace(new RegExp(`:${pathParam.name}`, 'g'), pathValue)
        .replace(new RegExp(`\\{${pathParam.name}\\}`, 'g'), pathValue)
    }
  })
  const params = preProcessParams(paramTarget.value?.requestParams).reduce((results, item) => {
    results[item.name] = processEvnParams(paramTarget.value.groupConfig, item.value)
    return results
  }, {})
  const { data, hasBody } = calcRequestBody(paramTarget)
  const headers = Object.assign(hasBody ? { 'content-type': paramTarget.value?.requestContentType } : {},
    preProcessParams(paramTarget.value?.headerParams).reduce((results, item) => {
      results[item.name] = processEvnParams(paramTarget.value.groupConfig, item.value)
      return results
    }, {}))
  let targetUrl = paramTarget.value?.targetUrl
  if (serverSend) {
    headers[SIMPLE_API_TARGET_URL_HEADER] = targetUrl
    headers[SIMPLE_API_ACCESS_TOKEN_HEADER] = useLoginConfigStore().accessToken
    targetUrl = '/admin/proxy' // 服务端代理发送
    if (apiDocDetail.value?.apiShare) {
      targetUrl = '/shares/proxy'
      headers[SIMPLE_API_ACCESS_TOKEN_HEADER] = useShareConfigStore().getShareToken(apiDocDetail.value.apiShare.shareId)
    }
  }
  const config = {
    loading: true,
    params,
    data,
    headers
  }
  let authContent = paramTarget.value.authContent
  if (authContent) {
    if (authContent.authType === AUTH_TYPE.INHERIT) {
      const preferenceId = apiDocDetail.value.apiShare?.shareId || projectInfoDetail.value?.projectCode || DEFAULT_PREFERENCE_ID_KEY
      const authModel = useShareConfigStore().sharePreferenceView[preferenceId]?.defaultAuthModel
      if (authModel) {
        authContent = authModel
      }
    }
    await AUTH_OPTION_CONFIG[authContent.authType]?.parseAuthInfo(authContent, headers, params, paramTarget)
  }
  previewRequest({
    url: joinPath(targetUrl, processedPath),
    method: apiDocDetail.value.method
  }, config).then(calcResponse, calcResponse)
}

const calcResponse = (response) => {
  responseTarget.value = processResponse(response)
  if (response.status === 200 && response.data?.includes('{"code":401')) {
    console.log('===============================responseTarget', responseTarget.value)
    emitter.emit('preview-401-error', {
      data: JSON.parse(responseTarget.value?.data)
    })
  }
}

const clearParamsAndResponse = () => {
  responseTarget.value = undefined
  paramTarget.value = undefined
}

defineExpose({
  toPreviewRequest,
  clearParamsAndResponse
})

</script>

<template>
  <el-container class="flex-column">
    <api-request-form
      v-if="paramTarget"
      v-model="paramTarget"
      :env-configs="envConfigs"
      :request-path="requestPath"
      :response-target="responseTarget"
      :schemas="schemas"
      @send-request="doDataPreview"
    />
  </el-container>
</template>

<style scoped>

</style>
