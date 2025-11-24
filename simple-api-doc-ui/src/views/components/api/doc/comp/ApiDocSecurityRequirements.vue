<script setup lang="jsx">
import { computed, useTemplateRef } from 'vue'
import {
  securitySchemas2List
} from '@/services/api/ApiDocEditService'
import { ElText } from 'element-plus'
import SecurityRequirementsWindow from '@/views/components/api/doc/comp/edit/SecurityRequirementsWindow.vue'

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
const securityRequirements = computed(() => {
  const securityRequirements = apiDocDetail.value?.securityRequirements
  return JSON.parse(securityRequirements?.schemaContent || '[]')
})
const supportedKeys = computed(() => securityRequirements.value.flatMap(config => Object.keys(config)))
const supportedSecurities = computed(() => {
  return securitySchemaList.value.filter(schema => supportedKeys.value.includes(schema.schemaName))
})
const hasSecurity = computed(() => !!supportedKeys.value?.length)
defineEmits(['schemaUpdated', 'toEditSecuritySchemas'])
const securityWinRef = useTemplateRef('securityWinRef')
const toEditSecuritySchemas = () => {
  securityWinRef.value.toEditSecuritySchemas()
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
        @click="toEditSecuritySchemas"
      >
        <common-icon
          :size="18"
          :icon="hasSecurity?'Edit':'Plus'"
        />
      </el-link>
    </h3>
    <div v-if="hasSecurity">
      <template
        v-for="({schema:security}, index) in supportedSecurities"
        :key="index"
      >
        <el-text
          class="margin-right1"
          tag="b"
        >
          {{ $t('api.label.authType') }}:
        </el-text>
        <el-text type="primary">
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
    <security-requirements-window
      ref="securityWinRef"
      :project-info-detail="apiDocDetail.projectInfoDetail"
      :api-doc-detail="apiDocDetail"
      @schema-updated="$emit('schemaUpdated')"
      @to-edit-security-schemas="$emit('toEditSecuritySchemas')"
    />
  </el-container>
</template>

<style scoped>

</style>
