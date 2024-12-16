<script setup>

import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import { computed } from 'vue'
import { $copyText, joinPath } from '@/utils'
import { $i18nBundle } from '@/messages'

const props = defineProps({
  envConfigs: {
    type: Array,
    default: () => []
  },
  viewAsEnabled: {
    type: Boolean,
    default: false
  },
  debugEnabled: {
    type: Boolean,
    default: false
  },
  authEnabled: {
    type: Boolean,
    default: false
  },
  authConfigured: {
    type: Boolean,
    default: false
  }
})

const apiDocDetail = defineModel({
  type: Object,
  default: () => ({})
})

const viewAsMarkdown = defineModel('viewAsMarkdown', {
  type: Boolean,
  default: false
})
const docFormOption = computed(() => {
  return {
    showLabel: false,
    type: 'select',
    prop: 'targetUrl',
    children: props.envConfigs.map(env => {
      return {
        value: env.url,
        label: env.name || $i18nBundle('api.label.defaultAddress')
      }
    }),
    attrs: {
      clearable: false,
      style: {
        width: '150px'
      }
    }
  }
})
defineEmits(['debug-api', 'config-auth', 'change-view-as'])
const requestUrl = computed(() => {
  return joinPath(apiDocDetail.value.targetUrl, apiDocDetail.value?.url)
})
</script>

<template>
  <el-header class="doc-path-header">
    <common-form-control
      :option="docFormOption"
      :model="apiDocDetail"
      class="margin-right2"
    />
    <span class="padding-top1 api-path-url">
      <api-method-tag
        size="large"
        effect="dark"
        :method="apiDocDetail?.method"
        class="margin-right2"
      />
      <el-link
        v-common-tooltip="requestUrl"
        type="primary"
        @click="$copyText(requestUrl)"
      >
        {{ apiDocDetail?.url }}
      </el-link>
    </span>
    <el-button
      v-if="viewAsEnabled"
      class="margin-top1"
      type="primary"
      plain
      style="padding-left: 10px;"
      @click="viewAsMarkdown=!viewAsMarkdown;$emit('change-view-as', viewAsMarkdown)"
    >
      <common-icon
        :icon="viewAsMarkdown?'custom-api':'custom-markdown'"
        :size="18"
      />
      {{ $t(viewAsMarkdown ? 'api.label.viewAsApi' : 'api.label.viewAsMarkdown') }}
    </el-button>
    <el-button
      v-if="debugEnabled"
      class="margin-top1"
      type="primary"
      style="padding-left: 10px;"
      @click="$emit('debug-api', apiDocDetail)"
    >
      <common-icon
        icon="PlayArrowFilled"
        :size="18"
      />
      {{ $t('api.label.debugAPI') }}
    </el-button>
    <el-badge
      v-if="authEnabled&&debugEnabled"
      type="primary"
      :hidden="!authConfigured"
      is-dot
      class="margin-top1 margin-left2"
    >
      <el-button
        v-if="authEnabled&&debugEnabled"
        type="success"
        style="padding-left: 10px;"
        @click="$emit('config-auth', apiDocDetail)"
      >
        <common-icon
          icon="Lock"
          :size="18"
        />
        {{ $t('api.label.authorization') }}
      </el-button>
    </el-badge>
  </el-header>
</template>

<style scoped>
.doc-path-header {
  display: flex;
  margin-top: 10px;
}
</style>
