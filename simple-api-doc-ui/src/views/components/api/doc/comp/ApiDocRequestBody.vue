<script setup>
import { computed } from 'vue'
import ApiDocSchemaTree from '@/views/components/api/doc/comp/ApiDocSchemaTree.vue'
import { calcShowMergeAllOf } from '@/services/api/ApiFolderService'
import { calcComponentMap } from '@/services/api/ApiDocPreviewService'
import { showGenerateSchemaSample } from '@/services/api/ApiCommonService'
import { MdPreview } from 'md-editor-v3'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'

const apiDocDetail = defineModel({
  type: Object,
  default: () => ({})
})

const projectInfoDetail = computed(() => {
  return apiDocDetail.value.projectInfoDetail
})
const showMergeAllOf = computed(() => calcShowMergeAllOf(apiDocDetail.value))
const componentMap = computed(() => calcComponentMap(projectInfoDetail.value.componentSchemas))
const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')
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
            <el-link
              v-if="requestsSchema.schemaContent"
              class="margin-left1"
              type="primary"
              @click="showGenerateSchemaSample(requestsSchema, componentMap)"
            >
              <common-icon
                :size="18"
                :icon="requestsSchema.contentType?.includes('xml') ? 'custom-icon-xml' : 'custom-icon-json'"
              />
            </el-link>
          </span>
        </template>
        <el-container class="flex-column">
          <md-preview
            v-if="requestsSchema.description"
            class="md-doc-container"
            :theme="theme"
            :model-value="requestsSchema.description"
          />
          <api-doc-schema-tree
            v-if="projectInfoDetail"
            :model-value="requestsSchema"
            :spec-version="projectInfoDetail.specVersion"
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
