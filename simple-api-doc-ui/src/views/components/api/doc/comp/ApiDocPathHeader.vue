<script setup>

import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import { computed } from 'vue'
import { $copyText } from '@/utils'

const props = defineProps({
  envConfigs: {
    type: Array,
    default: () => []
  },
  debugEnabled: {
    type: Boolean,
    default: false
  }
})

const apiDocDetail = defineModel({
  type: Object,
  default: () => ({})
})
const docFormOption = computed(() => {
  return {
    showLabel: false,
    type: 'select',
    prop: 'targetUrl',
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
defineEmits(['debug-api'])
</script>

<template>
  <el-header class="doc-path-header">
    <common-form-control
      :option="docFormOption"
      :model="apiDocDetail"
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
    <el-button
      v-if="debugEnabled"
      class="margin-top1"
      type="primary"
      style="padding-left: 5px;"
      @click="$emit('debug-api', apiDocDetail)"
    >
      <common-icon
        icon="PlayArrowFilled"
        :size="18"
      />
      {{ $t('api.label.debugAPI') }}
    </el-button>
  </el-header>
</template>

<style scoped>
.doc-path-header {
  display: flex;
  margin-top: 10px;
}
</style>
