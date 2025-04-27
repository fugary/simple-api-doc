<script setup>
import { ref, computed } from 'vue'
import { getFolderPaths } from '@/services/api/ApiProjectService'
import ApiDocHistoryViewer from '@/views/components/api/doc/comp/ApiDocHistoryViewer.vue'
import { useScreenCheck } from '@/services/api/ApiCommonService'
defineProps({
  editable: {
    type: Boolean,
    default: false
  },
  historyCount: {
    type: Number,
    default: 0
  }
})
const currentDoc = defineModel({
  type: Object,
  default: undefined
})
const folderPaths = computed(() => {
  if (currentDoc.value) {
    return getFolderPaths(currentDoc.value)
  }
  return []
})
const apiDocHistoryRef = ref()
const { isMobile } = useScreenCheck()
</script>

<template>
  <el-header
    style="min-height: var(--el-header-height);height:auto;"
    :style="isMobile?'padding-left: 50px;':''"
  >
    <el-breadcrumb
      v-if="folderPaths.length>1"
      class="margin-top3"
    >
      <el-breadcrumb-item
        v-for="(folderPath, index) in folderPaths"
        :key="index"
      >
        {{ folderPath }}
      </el-breadcrumb-item>
    </el-breadcrumb>
    <h2>
      {{ currentDoc?.docName || currentDoc?.url }}
      <el-button
        v-if="editable"
        class="margin-left2"
        type="primary"
        @click="currentDoc.editing=true"
      >
        {{ $t('common.label.edit') }}
      </el-button>
      <el-link
        v-if="editable&&historyCount"
        class="margin-left2"
        type="primary"
        @click="apiDocHistoryRef?.showHistoryList(currentDoc.id)"
      >
        {{ $t('api.label.historyVersions') }}
        <el-text type="info">
          ({{ historyCount }})
        </el-text>
      </el-link>
      <!-- 添加修改人和修改时间 -->
      <el-row v-if="currentDoc&&(currentDoc.modifier||currentDoc.creator)">
        <el-col>
          <el-text type="info">
            {{ $t('common.label.modifier') }}: {{ currentDoc.modifier||currentDoc.creator }}
          </el-text>
          <el-text
            type="info"
            class="margin-left3"
          >
            {{ $t('common.label.modifyDate') }}: {{ $date(currentDoc.modifyDate||currentDoc.createDate, 'YYYY-MM-DD HH:mm') }}
          </el-text>
        </el-col>
      </el-row>
      <api-doc-history-viewer
        v-if="historyCount"
        ref="apiDocHistoryRef"
      />
    </h2>
  </el-header>
</template>

<style scoped>

</style>
