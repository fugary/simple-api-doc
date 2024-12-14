<script setup>
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
        <el-container
          v-if="projectInfoDetail"
          class="flex-column"
        >
          <api-doc-schema-tree
            v-if="!viewAsMarkdown"
            :model-value="requestsSchema"
            :spec-version="projectInfoDetail.specVersion"
            :component-schemas="projectInfoDetail.componentSchemas"
            :show-merge-all-of="showMergeAllOf"
          />
          <api-doc-schema-table
            v-else
            :model-value="requestsSchema"
            :component-schemas="projectInfoDetail.componentSchemas"
            :show-merge-all-of="showMergeAllOf"
          />
        </el-container>
      </el-tab-pane>
    </el-tabs>
  </el-container>
</template>

<style scoped>

</style>
