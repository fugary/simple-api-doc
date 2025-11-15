<script setup>
import { computed } from 'vue'
import ApiDocSchemaTree from '@/views/components/api/doc/comp/ApiDocSchemaTree.vue'
import { calcShowMergeAllOf } from '@/services/api/ApiFolderService'
import { calcComponentMap } from '@/services/api/ApiDocPreviewService'
import { showGenerateSchemaSample } from '@/services/api/ApiCommonService'
import { MdPreview } from 'md-editor-v3'
import { toEditComponent } from '@/utils/DynamicUtils'
import { newDocInfoDetail } from '@/services/api/ApiDocEditService'

defineProps({
  theme: {
    type: String,
    default: 'dark'
  },
  editable: {
    type: Boolean,
    default: false
  }
})
const apiDocDetail = defineModel({
  type: Object,
  default: () => ({})
})

const projectInfoDetail = computed(() => {
  return apiDocDetail.value.projectInfoDetail
})
const showMergeAllOf = computed(() => calcShowMergeAllOf(apiDocDetail.value))
const componentMap = computed(() => calcComponentMap(projectInfoDetail.value.componentSchemas))
const emit = defineEmits(['schemaUpdated'])
const toEditRequestSchema = (requestsSchema) => {
  if (!requestsSchema) {
    requestsSchema = {
      ...newDocInfoDetail(apiDocDetail.value),
      bodyType: 'request',
      schemaContent: '{"schema":{"type":"object"}}',
      contentType: 'application/json'
    }
  }
  toEditComponent(requestsSchema, {
    onSaveComponent: () => emit('schemaUpdated'),
    onDeleteComponent: () => emit('schemaUpdated')
  })
}
</script>

<template>
  <el-container class="flex-column">
    <h3 id="api-doc-parameters">
      {{ $t('api.label.requestBody') }}
      <el-link
        v-if="editable"
        class="margin-left1"
        type="primary"
        @click="toEditRequestSchema()"
      >
        <common-icon
          :size="18"
          icon="Plus"
        />
      </el-link>
    </h3>
    <el-tabs v-if="apiDocDetail.requestsSchemas?.length">
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
            <el-link
              v-if="editable"
              class="margin-left1"
              type="primary"
              @click="toEditRequestSchema(requestsSchema)"
            >
              <common-icon
                :size="18"
                icon="Edit"
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
    <el-container v-else>
      <el-text type="info">
        {{ $t('common.msg.noData') }}
      </el-text>
    </el-container>
  </el-container>
</template>

<style scoped>

</style>
