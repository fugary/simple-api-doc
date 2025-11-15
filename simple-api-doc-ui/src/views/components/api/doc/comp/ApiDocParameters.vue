<script setup lang="jsx">
import { computed } from 'vue'
import ApiDocSchemaTree from '@/views/components/api/doc/comp/ApiDocSchemaTree.vue'
import { calcShowMergeAllOf } from '@/services/api/ApiFolderService'
import { toEditApiDocRequestParams } from '@/utils/DynamicUtils'

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

const projectInfoDetail = computed(() => {
  return apiDocDetail.value.projectInfoDetail
})
const showMergeAllOf = computed(() => calcShowMergeAllOf(apiDocDetail.value))
const emit = defineEmits(['schemaUpdated'])
const toEditApiDocParameters = () => {
  toEditApiDocRequestParams(apiDocDetail.value, {
    onSaveComponent: () => emit('schemaUpdated'),
    onDeleteComponent: () => emit('schemaUpdated')
  })
}
const hasParameters = computed(() => {
  const parametersSchema = apiDocDetail.value?.parametersSchema
  return parametersSchema?.schemaContent && parametersSchema?.schemaContent !== '[]'
})
</script>

<template>
  <el-container class="flex-column">
    <h3 id="api-doc-parameters">
      {{ $t('api.label.queryParams') }}
      <el-link
        v-if="editable"
        class="margin-left1"
        type="primary"
        @click="toEditApiDocParameters(apiDocDetail)"
      >
        <common-icon
          :size="18"
          :icon="hasParameters?'Edit':'Plus'"
        />
      </el-link>
    </h3>
    <api-doc-schema-tree
      v-if="projectInfoDetail&&hasParameters"
      v-model="apiDocDetail.parametersSchema"
      :spec-version="projectInfoDetail.specVersion"
      :component-schemas="projectInfoDetail.componentSchemas"
      :show-merge-all-of="showMergeAllOf"
    />
    <el-container v-else>
      <el-text type="info">
        {{ $t('common.msg.noData') }}
      </el-text>
    </el-container>
  </el-container>
</template>

<style scoped>

</style>
