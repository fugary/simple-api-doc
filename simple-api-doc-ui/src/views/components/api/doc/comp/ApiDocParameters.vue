<script setup lang="jsx">
import { computed } from 'vue'
import ApiDocSchemaTree from '@/views/components/api/doc/comp/ApiDocSchemaTree.vue'

const apiDocDetail = defineModel({
  type: Object,
  default: () => ({})
})

const projectInfoDetail = computed(() => {
  return apiDocDetail.value.projectInfoDetail
})

</script>

<template>
  <el-container class="flex-column">
    <h3 id="api-doc-parameters">
      {{ $t('api.label.queryParams') }}
    </h3>
    <api-doc-schema-tree
      v-if="projectInfoDetail && apiDocDetail.parametersSchema"
      v-model="apiDocDetail.parametersSchema"
      :spec-version="projectInfoDetail.specVersion"
      :component-schemas="projectInfoDetail.componentSchemas"
    />
    <el-container v-else>
      无
    </el-container>
  </el-container>
</template>

<style scoped>

</style>
