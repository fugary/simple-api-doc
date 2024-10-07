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
      {{ $t('api.label.responseBody') }}
    </h3>
    <el-tabs>
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
        <el-container class="flex-column">
          <el-container class="padding-10">
            <el-text
              type="info"
              class="margin-right3"
            >
              Content Type:
              <el-text>{{ responseSchema.contentType }}</el-text>
            </el-text>
            <el-text
              v-if="responseSchema.statusCode"
              type="info"
              class="margin-right3"
            >
              Status Code:
              <el-text>{{ responseSchema.statusCode }}</el-text>
            </el-text>
          </el-container>
          <api-doc-schema-tree
            v-if="projectInfoDetail"
            :model-value="responseSchema"
            :spec-version="projectInfoDetail.specVersion"
            :component-schemas="projectInfoDetail.componentSchemas"
          />
        </el-container>
      </el-tab-pane>
    </el-tabs>
  </el-container>
</template>

<style scoped>

</style>
