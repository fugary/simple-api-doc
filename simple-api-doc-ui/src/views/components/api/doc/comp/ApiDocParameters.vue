<script setup lang="jsx">
import { computed } from 'vue'
import ApiDocSchemaTree from '@/views/components/api/doc/comp/ApiDocSchemaTree.vue'
import { calcShowMergeAllOf } from '@/services/api/ApiFolderService'
import ApiDocSchemaTable from '@/views/components/api/doc/comp/ApiDocSchemaTable.vue'

const apiDocDetail = defineModel({
  type: Object,
  default: () => ({})
})

defineProps({
  viewAsMarkdown: {
    type: Boolean,
    default: false
  }
})

const projectInfoDetail = computed(() => {
  return apiDocDetail.value.projectInfoDetail
})
const showMergeAllOf = computed(() => calcShowMergeAllOf(apiDocDetail.value))
</script>

<template>
  <el-container class="flex-column">
    <h3 id="api-doc-parameters">
      {{ $t('api.label.queryParams') }}
    </h3>
    <template v-if="projectInfoDetail && apiDocDetail.parametersSchema">
      <api-doc-schema-tree
        v-if="!viewAsMarkdown"
        v-model="apiDocDetail.parametersSchema"
        :spec-version="projectInfoDetail.specVersion"
        :component-schemas="projectInfoDetail.componentSchemas"
        :show-merge-all-of="showMergeAllOf"
      />
      <api-doc-schema-table
        v-else
        v-model="apiDocDetail.parametersSchema"
        :component-schemas="projectInfoDetail.componentSchemas"
        :show-merge-all-of="showMergeAllOf"
      />
    </template>
    <el-container v-else>
      无
    </el-container>
  </el-container>
</template>

<style scoped>

</style>
