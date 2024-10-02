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
      响应数据
    </h3>
    <el-tabs type="border-card">
      <el-tab-pane
        v-for="(responseSchema, index) in apiDocDetail.responsesSchemas"
        :key="index"
        lazy
      >
        <template #label>
          <span>
            {{ responseSchema.schemaName }}
            <el-text
              type="info"
              size="small"
            >
              {{ responseSchema.description }}
            </el-text>
          </span>
        </template>
        {{ responseSchema }}
        <api-doc-schema-tree
          v-if="projectInfoDetail"
          :model-value="responseSchema"
          :spec-version="projectInfoDetail.specVersion"
          :component-schemas="projectInfoDetail.componentSchemas"
        />
      </el-tab-pane>
    </el-tabs>
  </el-container>
</template>

<style scoped>

</style>
