<script setup>

import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import { computed } from 'vue'
import { $copyText, joinPath } from '@/utils'
import { getEnvOptions } from '@/api/SimpleShareApi'

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
    children: getEnvOptions(props.envConfigs),
    attrs: {
      clearable: false,
      style: {
        width: '150px'
      }
    }
  }
})
defineEmits(['debug-api', 'config-auth', 'change-view-as', 'request-code'])
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
        <del v-if="apiDocDetail?.deprecated">{{ apiDocDetail?.url }}</del>
        <span v-else>{{ apiDocDetail?.url }}</span>
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
    <el-dropdown
      v-if="debugEnabled"
      class="margin-top1 margin-left2"
      type="primary"
      split-button
      trigger="click"
      @click="$emit('debug-api', apiDocDetail)"
      @command="$emit('request-code')"
    >
      <common-icon
        icon="PlayArrowFilled"
        :size="18"
      />
      {{ $t('api.label.debugAPI') }}
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="request-code">
            <common-icon
              icon="CodeFilled"
              :size="18"
            />
            {{ $t('api.label.requestCode') }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
    <el-button
      v-else
      class="margin-top1"
      @click="$emit('request-code')"
    >
      <common-icon
        icon="CodeFilled"
        :size="18"
      />
      {{ $t('api.label.requestCode') }}
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
  margin-top: 5px;
}
</style>
