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
      {{ $t('api.label.requestBody') }}
    </h3>
    <el-tabs>
      <el-tab-pane
        v-for="(requestsSchema, index) in apiDocDetail.requestsSchemas"
        :key="index"
        lazy
      >
        <template #label>
          <span>
            {{ $t('api.label.requestBody') }}
            <el-text
              type="info"
              size="small"
            >
              {{ requestsSchema.contentType }}
            </el-text>
          </span>
        </template>
        <el-container class="flex-column">
          <api-doc-schema-tree
            v-if="projectInfoDetail"
            :model-value="requestsSchema"
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
