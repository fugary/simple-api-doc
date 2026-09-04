<script setup>
import { computed } from 'vue'
import ApiRequestFormRes from '@/views/components/api/form/ApiRequestFormRes.vue'
import ApiRequestFormReq from '@/views/components/api/form/ApiRequestFormReq.vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import { $copyText, joinPath, addParamsToURL } from '@/utils'
import { extractQueryParams, isGetMethod } from '@/services/api/ApiDocPreviewService'
import { processEvnParams } from '@/services/api/ApiCommonService'
import { getEnvOptions } from '@/api/SimpleShareApi'

const props = defineProps({
  envConfigs: {
    type: Array,
    default: () => []
  },
  responseTarget: {
    type: Object,
    default: () => undefined
  },
  requestPath: {
    type: String,
    required: true
  },
  formHeight: {
    type: String,
    default: ''
  }
})
const paramTarget = defineModel('modelValue', {
  type: Object,
  default: () => ({})
})

const requestUrl = computed(() => {
  let reqUrl = props.requestPath
  paramTarget.value?.pathParams?.filter(pathParam => !!pathParam.value).forEach(pathParam => {
    reqUrl = reqUrl.replace(new RegExp(`:${pathParam.name}`, 'g'), pathParam.value)
      .replace(new RegExp(`\\{${pathParam.name}\\}`, 'g'), processEvnParams(paramTarget.value.groupConfig, pathParam.value, true))
  })
  const paramsSendAs = paramTarget.value?.paramsSendAs || 'urlParams'
  const isUrlParams = isGetMethod(paramTarget.value?.method) || paramsSendAs === 'urlParams'
  if (isUrlParams) {
    const calcReqParams = extractQueryParams(paramTarget.value?.requestParams, paramTarget.value?.groupConfig)
    reqUrl = addParamsToURL(reqUrl, calcReqParams)
  }
  return joinPath(paramTarget.value.targetUrl, reqUrl)
})

const emit = defineEmits(['sendRequest', 'resetRequestForm'])

const sendRequest = (form) => {
  form.validate(valid => {
    if (valid) {
      console.log('===============================发送请求', valid, paramTarget.value)
      emit('sendRequest', paramTarget.value)
    }
  })
}

const requestExamples = computed(() => {
  const examples = paramTarget.value?.requestExamples
  if (examples?.length) {
    return examples.flatMap(example => {
      if (!example) return []
      try {
        const parsed = JSON.parse(example)
        return Array.isArray(parsed) ? parsed : [parsed]
      } catch {
        return []
      }
    })
  }
  return []
})

const docFormOption = computed(() => {
  return {
    labelWidth: '1px',
    showLabel: false,
    type: 'select',
    prop: 'targetUrl',
    children: getEnvOptions(props.envConfigs),
    attrs: {
      clearable: false,
      style: 'width: 150px'
    }
  }
})

</script>

<template>
  <el-container class="flex-column">
    <common-form
      :show-buttons="false"
      :model="paramTarget"
    >
      <template #default="{form}">
        <div
          class="el-header"
          style="display: flex;"
        >
          <common-form-control
            :option="docFormOption"
            :model="paramTarget"
            class="margin-right2"
          />
          <div
            class="api-path-url padding-top1"
          >
            <api-method-tag
              size="large"
              effect="dark"
              :method="paramTarget?.method"
              class="margin-right2"
            />
            <el-link
              v-common-tooltip="requestUrl"
              type="primary"
              class="api-url-link"
              @click="$copyText(requestUrl)"
            >
              <span class="api-url-text">{{ requestUrl }}</span>
            </el-link>
          </div>
          <div
            class="padding-top1"
            style="margin-left: auto;"
          >
            <el-button
              type="primary"
              @click="sendRequest(form)"
            >
              {{ $t('api.label.sendRequest') }}
            </el-button>
          </div>
        </div>
        <el-container
          class="flex-column"
          :style="{height:formHeight, overflow:'auto'}"
        >
          <ApiRequestFormReq
            v-model="paramTarget"
            show-authorization
            :response-target="responseTarget"
            :schema-type="paramTarget.requestContentType"
            :schema-body="paramTarget.requestBodySchema"
            :examples="requestExamples"
            @reset-request-form="$emit('resetRequestForm')"
          />
          <ApiRequestFormRes
            v-if="responseTarget"
            v-model="paramTarget"
            :response-target="responseTarget"
          />
        </el-container>
      </template>
    </common-form>
  </el-container>
</template>

<style scoped>
.api-path-url {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: flex-start;
  margin-right: 10px;
}
.api-url-link {
  min-height: 32px;
  max-width: calc(100% - 70px);
  justify-content: flex-start;
  text-align: left;
}
.api-url-link :deep(.el-link__inner) {
  min-height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  max-width: 100%;
  text-align: left;
}
.api-url-text {
  text-align: left;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-all;
  white-space: normal;
  line-height: 1.4;
  max-height: 2.8em;
}
</style>
