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
    <div style="display: flex;">
      <h3
        id="api-doc-parameters"
        class="margin-right2"
      >
        Body参数
      </h3>
      <el-text
        v-if="apiDocDetail.requestsSchemas?.[0].contentType"
        type="info"
      >
        {{ apiDocDetail.requestsSchemas?.[0].contentType }}
      </el-text>
    </div>
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
