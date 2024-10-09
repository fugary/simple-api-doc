<script setup>
import { computed } from 'vue'
import UrlCopyLink from '@/views/components/api/UrlCopyLink.vue'
import ApiRequestFormRes from '@/views/components/api/form/ApiRequestFormRes.vue'
import ApiRequestFormReq from '@/views/components/api/form/ApiRequestFormReq.vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import { $copyText, joinPath } from '@/utils'

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
      .replace(new RegExp(`\\{${pathParam.name}\\}`, 'g'), pathParam.value)
  })
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
  return examples ? JSON.parse(examples) : []
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
        label: env.name || '默认地址'
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
            style="margin-right:auto;"
          >
            <api-method-tag
              size="large"
              effect="dark"
              :method="paramTarget?.method"
              class="margin-right2"
            />
            <el-link
              type="primary"
              @click="$copyText(requestUrl)"
            >
              {{ requestUrl }}
            </el-link>
            <url-copy-link
              class="margin-left1 margin-top1"
              :url-path="requestUrl"
            />
          </div>
          <div class="padding-top1">
            <el-button
              type="primary"
              @click="sendRequest(form)"
            >
              {{ $t('api.label.sendRequest') }}
            </el-button>
          </div>
        </div>
        <ApiRequestFormReq
          v-model="paramTarget"
          show-authorization
          :response-target="responseTarget"
          :schema-type="paramTarget.requestContentType"
          :schema-body="paramTarget.requestBodySchema"
          :examples="requestExamples"
        />
      </template>
    </common-form>
    <ApiRequestFormRes
      v-if="responseTarget"
      v-model="paramTarget"
      :response-target="responseTarget"
      :examples="responseExamples"
    />
  </el-container>
</template>

<style scoped>

</style>
