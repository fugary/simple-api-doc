<script setup>

import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import { computed } from 'vue'
import { $copyText } from '@/utils'

const props = defineProps({
  apiDocDetail: {
    type: Object,
    default: undefined
  },
  envConfigs: {
    type: Array,
    default: () => []
  },
  debugEnabled: {
    type: Boolean,
    default: true
  }
})

const docParam = defineModel({
  type: Object,
  default: () => ({})
})
const docFormOption = computed(() => {
  return {
    showLabel: false,
    type: 'select',
    prop: 'baseUrl',
    children: props.envConfigs.map(env => {
      return {
        value: env.url,
        label: env.name || '默认地址'
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
</script>

<template>
  <el-header class="doc-path-header">
    <common-form-control
      :option="docFormOption"
      :model="docParam"
      class="margin-right2"
    />
    <span
      class="padding-top1"
      style="margin-right: auto;"
    >
      <api-method-tag
        size="large"
        effect="dark"
        :method="apiDocDetail?.method"
        class="margin-right2"
      />
      <el-link
        type="primary"
        @click="$copyText(apiDocDetail?.url)"
      >
        {{ apiDocDetail?.url }}
      </el-link>
    </span>
    <span
      v-if="debugEnabled"
      class="padding-top1"
    >
      <el-button
        type="primary"
        style="padding-left: 5px;"
      >
        <common-icon
          icon="PlayArrowFilled"
          :size="18"
        />
        {{ $t('api.label.debugAPI') }}
      </el-button>
    </span>
  </el-header>
</template>

<style scoped>
.doc-path-header {
  display: flex;
  margin-top: 10px;
}
</style>
