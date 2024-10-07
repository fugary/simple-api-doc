<script setup>
import { computed } from 'vue'
import UrlCopyLink from '@/views/components/api/UrlCopyLink.vue'
import ApiRequestFormRes from '@/views/components/api/form/ApiRequestFormRes.vue'
import ApiRequestFormReq from '@/views/components/api/form/ApiRequestFormReq.vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import { $copyText } from '@/utils'

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
  schemas: {
    type: Array,
    default: () => []
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
  return reqUrl
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

const schema = computed(() => {
  return props.schemas[0]
})

const requestExamples = computed(() => {
  const examples = schema.value?.requestExamples
  return examples ? JSON.parse(examples) : []
})

const responseExamples = computed(() => {
  const examples = schema.value?.responseExamples
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
      clearable: false
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
        <el-row class="el-header">
          <el-col :span="4">
            <common-form-control
              :option="docFormOption"
              :model="paramTarget"
              class="margin-top1 margin-right2"
            />
          </el-col>
          <el-col
            :span="14"
            class="padding-top1"
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
          </el-col>
          <el-col
            :span="4"
            class="padding-top1 padding-left2"
          >
            <el-button
              type="primary"
              @click="sendRequest(form)"
            >
              {{ $t('api.label.sendRequest') }}
            </el-button>
          </el-col>
        </el-row>
        <ApiRequestFormReq
          v-model="paramTarget"
          show-authorization
          :response-target="responseTarget"
          :schema-type="schema?.requestMediaType"
          :schema-body="schema?.requestBodySchema"
          :examples="requestExamples"
        />
      </template>
    </common-form>
    <ApiRequestFormRes
      v-if="responseTarget"
      v-model="paramTarget"
      :response-target="responseTarget"
      :schema-type="schema?.responseMediaType"
      :schema-body="schema?.responseBodySchema"
      :examples="responseExamples"
    />
  </el-container>
</template>

<style scoped>

</style>
