<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import {
  addRequestParamsToResult,
  calcParamTarget,
  calcRequestBody,
  preProcessParams,
  previewRequest,
  processResponse
} from '@/services/api/ApiDocPreviewService'
import ApiRequestForm from '@/views/components/api/form/ApiRequestForm.vue'
import { AUTH_OPTION_CONFIG } from '@/services/api/ApiAuthorizationService'
import { calcPreviewHeaders, processEvnParams } from '@/services/api/ApiCommonService'
import { joinPath, toGetParams } from '@/utils'
import {
  AUTH_TYPE,
  SIMPLE_API_ACCESS_TOKEN_HEADER,
  SIMPLE_API_TARGET_URL_HEADER
} from '@/consts/ApiConstants'
import { useLoginConfigStore } from '@/stores/LoginConfigStore'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import emitter from '@/vendors/emitter'
import { getEnvConfigs } from '@/api/SimpleShareApi'
import { isFunction, lowerCase, isString } from 'lodash-es'
import { calcDetailPreferenceId } from '@/services/api/ApiFolderService'

const projectInfoDetail = ref()
const apiDocDetail = ref()
const envConfigs = ref([])
const paramTarget = ref()
const responseTarget = ref()

defineOptions({
  inheritAttrs: false
})

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

const resetParamTarget = () => {
  paramTarget.value = calcParamTarget(projectInfoDetail.value, apiDocDetail.value)
  envConfigs.value = getEnvConfigs(apiDocDetail.value)
  paramTarget.value.targetUrl = apiDocDetail.value.targetUrl || envConfigs.value[0]?.url
}

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
    addRequestParamsToResult(results, item.name, processEvnParams(paramTarget.value.groupConfig, item.value))
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
    paramsSerializer: toGetParams,
    data,
    headers
  }
  if (hasBody && isString(data)) { // 字符串不让axios处理，防止调试请求和postman有差异
    config.transformRequest = req => req
  }
  calcPreviewHeaders(config)
  let authContent = paramTarget.value.authContent
  if (authContent) {
    if (authContent.authType === AUTH_TYPE.INHERIT) {
      const preferenceId = calcDetailPreferenceId(apiDocDetail.value)
      const authModel = useShareConfigStore().sharePreferenceView[preferenceId]?.defaultAuthModel
      const securityRequirements = paramTarget.value?.securityRequirements || []
      if (authModel?.authModels?.length && securityRequirements.length) {
        const supportedModels = authModel.authModels.filter(model => securityRequirements?.includes(lowerCase(model.authKey)))
        authContent = supportedModels.find(model => model.authType === authModel.authType) || supportedModels[0]
      }
    }
    await AUTH_OPTION_CONFIG[authContent?.authType]?.parseAuthInfo(authContent, headers, params, paramTarget)
  }
  previewRequest({
    url: joinPath(targetUrl, processedPath),
    method: apiDocDetail.value.method
  }, config).then(calcResponse, calcResponse)
}

const calcResponse = (response) => {
  responseTarget.value = processResponse(response)
  if (response.status === 200 && response.data?.includes?.('{"code":401')) {
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
      v-bind="$attrs"
      @send-request="doDataPreview"
      @reset-request-form="resetParamTarget"
    />
  </el-container>
</template>

<style scoped>

</style>
