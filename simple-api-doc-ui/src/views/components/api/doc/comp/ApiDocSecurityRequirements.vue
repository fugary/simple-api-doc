<script setup lang="jsx">
import { computed } from 'vue'
import {
  securitySchemas2List
} from '@/services/api/ApiDocEditService'
import { ElText } from 'element-plus'
import { toEditSecuritySchemas } from '@/utils/DynamicUtils'
import { calcSecurityRequirements } from '@/services/api/ApiDocPreviewService'
import { lowerCase } from 'lodash-es'

defineProps({
  editable: {
    type: Boolean,
    default: false
  }
})

const apiDocDetail = defineModel({
  type: Object,
  default: () => ({})
})
const securitySchemaList = computed(() => {
  const securitySchemas = JSON.parse(apiDocDetail.value.projectInfoDetail?.securitySchemas?.schemaContent || '{}')
  return securitySchemas2List(securitySchemas)
})
// doc支持的
const supportedKeys = computed(() => calcSecurityRequirements(apiDocDetail.value))
const defaultSupportedKeys = computed(() => calcSecurityRequirements(apiDocDetail.value?.projectInfoDetail))
const supportedSecurities = computed(() => {
  return securitySchemaList.value.filter(schema => {
    const name = lowerCase(schema.schemaName)
    const docSupport = supportedKeys.value.includes(name)
    const defaultSupport = defaultSupportedKeys.value.includes(name)
    schema.defaultFlag = defaultSupport && !docSupport
    return docSupport || defaultSupport
  })
})
const hasSecurity = computed(() => !!supportedSecurities.value?.length)
const emit = defineEmits(['schemaUpdated', 'toEditSecuritySchemas'])
const toEditDocSecuritySchemas = () => {
  toEditSecuritySchemas({
    apiDocDetail: apiDocDetail.value,
    projectInfoDetail: apiDocDetail.value.projectInfoDetail,
    onSchemaUpdated: (...args) => emit('schemaUpdated', ...args),
    onToEditSecuritySchemas: (...args) => emit('toEditSecuritySchemas', ...args)
  })
}
</script>

<template>
  <el-container class="flex-column">
    <h3>
      {{ $t('api.label.authorization') }}
      <el-link
        v-if="editable"
        class="margin-left1"
        type="primary"
        @click="toEditDocSecuritySchemas"
      >
        <common-icon
          :size="18"
          :icon="hasSecurity?'Edit':'Plus'"
        />
      </el-link>
    </h3>
    <div v-if="hasSecurity">
      <template
        v-for="({schema:security, defaultFlag}, index) in supportedSecurities"
        :key="index"
      >
        <el-text
          class="margin-right1"
          tag="b"
        >
          {{ $t('api.label.authType') }}:
        </el-text>
        <el-tag
          v-if="defaultFlag"
          type="success"
          class="margin-right1"
        >
          {{ $t('api.label.authTypeInherit') }}
        </el-tag>
        <el-text
          type="primary"
          class="margin-right1"
        >
          {{ security.name }}
        </el-text>
        <el-text
          v-if="security.in"
          type="info"
        >
          ({{ security.in }})
        </el-text>
        <el-text
          v-if="security.type"
          type="info"
        >
          &lt;{{ security.type }}&gt;
        </el-text>
        <el-text
          v-if="security.description"
          type="info"
        >
          {{ security.description }}
        </el-text>
        <pre />
      </template>
    </div>
    <el-container v-else>
      <el-text type="info">
        {{ $t('common.msg.noData') }}
      </el-text>
    </el-container>
  </el-container>
</template>

<style scoped>

</style>
