<script setup>
import { computed } from 'vue'
import ApiRequestFormRes from '@/views/components/api/form/ApiRequestFormRes.vue'
import ApiRequestFormReq from '@/views/components/api/form/ApiRequestFormReq.vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import { $copyText, joinPath, addParamsToURL } from '@/utils'
import { $i18nBundle } from '@/messages'
import { addRequestParamsToResult } from '@/services/api/ApiDocPreviewService'
import { processEvnParams } from '@/services/api/ApiCommonService'

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
  paramTarget.value?.pathParams?.forEach(pathParam => {
    reqUrl = reqUrl.replace(new RegExp(`:${pathParam.name}`, 'g'), pathParam.value)
      .replace(new RegExp(`\\{${pathParam.name}\\}`, 'g'), processEvnParams(paramTarget.value.groupConfig, pathParam.value))
  })
  if (paramTarget.value?.method?.toLowerCase() === 'get') {
    const calcReqParams = paramTarget.value?.requestParams?.filter(requestParam => !!requestParam.name && requestParam.enabled).reduce((results, item) => {
      return addRequestParamsToResult(results, item.name, processEvnParams(paramTarget.value.groupConfig, item.value))
    }, {})
    reqUrl = addParamsToURL(reqUrl, calcReqParams)
  }
  return joinPath(paramTarget.value.targetUrl, reqUrl)
})

const emit = defineEmits(['sendRequest'])

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
    return examples.map(example => JSON.parse(example))
  }
  return []
})

const docFormOption = computed(() => {
  return {
    labelWidth: '1px',
    showLabel: false,
    type: 'select',
    prop: 'targetUrl',
    children: props.envConfigs?.map(env => {
      return {
        value: env.url,
        label: env.name || $i18nBundle('api.label.defaultAddress')
      }
    }),
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
            style="margin-right: 10px"
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
              style="white-space: break-spaces;word-break: break-all;"
              @click="$copyText(requestUrl)"
            >
              {{ requestUrl }}
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

</style>
