<script setup>
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
      Body参数
    </h3>
    <api-doc-schema-tree
      v-if="projectInfoDetail"
      v-model="apiDocDetail.requestsSchemas"
      :spec-version="projectInfoDetail.specVersion"
      :component-schemas="projectInfoDetail.componentSchemas"
    />
  </el-container>
</template>

<style scoped>

</style>
