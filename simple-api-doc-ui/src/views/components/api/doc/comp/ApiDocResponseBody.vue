<script setup>
import { computed } from 'vue'
import ApiDocSchemaTree from '@/views/components/api/doc/comp/ApiDocSchemaTree.vue'
import { calcShowMergeAllOf } from '@/services/api/ApiFolderService'
import { showGenerateSchemaSample } from '@/services/api/ApiCommonService'
import { calcComponentMap } from '@/services/api/ApiDocPreviewService'
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
const responsesSchemas = computed(() => {
  return apiDocDetail.value.responsesSchemas?.toSorted((a, b) => {
    const statusA = a.statusCode || 600
    const statusB = b.statusCode || 600
    return statusA - statusB
  })
})
const componentMap = computed(() => calcComponentMap(projectInfoDetail.value.componentSchemas))
const theme = computed(() => useGlobalConfigStore().isDarkTheme ? 'dark' : 'light')
</script>

<template>
  <el-container class="flex-column">
    <h3 id="api-doc-parameters">
      {{ $t('api.label.responseBody') }}
    </h3>
    <el-tabs class="padding-right2">
      <el-tab-pane
        v-for="(responseSchema, index) in responsesSchemas"
        :key="index"
        lazy
      >
        <template #label>
          <span>
            {{ responseSchema.schemaName }}
          </span>
          <el-text
            type="info"
            size="small"
            class="response-tab-truncated margin-left1"
            truncated
          >
            {{ responseSchema.description }}
          </el-text>
        </template>
        <el-container class="flex-column">
          <el-container class="padding-10">
            <el-text
              v-if="responseSchema.contentType"
              type="info"
              class="margin-right3"
            >
              Content Type:
              <el-text>{{ responseSchema.contentType }}</el-text>
              <el-link
                v-if="responseSchema.schemaContent"
                class="margin-left1"
                type="primary"
                @click="showGenerateSchemaSample(responseSchema, componentMap)"
              >
                <common-icon
                  :size="18"
                  :icon="responseSchema.contentType?.includes('xml') ? 'custom-icon-xml' : 'custom-icon-json'"
                />
              </el-link>
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
          <md-preview
            v-if="responseSchema.description"
            class="md-doc-container"
            :theme="theme"
            :model-value="responseSchema.description"
          />
          <api-doc-schema-tree
            v-if="projectInfoDetail"
            :model-value="responseSchema"
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
