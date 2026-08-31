<script setup>
import { ref, onMounted, inject, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getRecentImports } from '@/api/dashboard/DashboardApi'
import { formatDate } from '@/utils'
import dayjs from 'dayjs'

const router = useRouter()
const all = inject('dashboard-all', ref(false))
const loading = ref(false)
const recentImports = ref([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await getRecentImports(all.value)
    if (res && res.success) {
      recentImports.value = res.resultData || []
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})

watch(all, () => {
  loadData()
})

const openTask = (task) => {
  router.push({
    name: 'ApiProjectTasks',
    query: { taskId: task.id }
  })
}

const getStatusColor = (status) => {
  switch (status) {
    case 'done': return 'var(--el-color-success)'
    case 'error': return 'var(--el-color-danger)'
    case 'running': return 'var(--el-color-primary)'
    case 'stopped': return 'var(--el-color-warning)'
    default: return 'var(--el-color-info)'
  }
}

const getProjectName = (item) => {
  return item.project?.projectName?.trim() || item.project?.projectCode
}
</script>

<template>
  <el-card
    shadow="hover"
    style="height: 100%; border-radius: 8px; display: flex; flex-direction: column;"
    body-style="flex: 1; padding: 0; overflow: hidden;"
  >
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center;">
        <span style="font-weight: bold; font-size: 16px; display: flex; align-items: center; gap: 8px;">
          <common-icon icon="Refresh" />
          {{ $t('api.label.dashboardRecentImports') }}
        </span>
        <el-link
          type="primary"
          :underline="false"
          @click="router.push({ name: 'ApiProjectImportTasks' })"
        >
          {{ $t('api.label.dashboardViewMore') }}
        </el-link>
      </div>
    </template>

    <div
      v-loading="loading"
      class="dashboard-list-container"
    >
      <el-empty
        v-if="!recentImports.length"
        :description="$t('api.label.dashboardNoData')"
        :image-size="60"
      />
      <div
        v-for="item in recentImports"
        :key="item.id"
        class="activity-item"
        @click="openTask(item)"
      >
        <el-avatar
          :size="36"
          :style="{ backgroundColor: getStatusColor(item.taskStatus), flexShrink: 0, marginRight: '12px' }"
        >
          <common-icon
            icon="DocumentAdd"
            :size="18"
          />
        </el-avatar>

        <div class="dashboard-item-main">
          <div class="dashboard-item-header">
            <el-text
              tag="b"
              truncated
              class="dashboard-item-title"
            >
              {{ item.taskName }}
            </el-text>
            <el-tooltip
              v-if="item.taskType === 'auto'"
              :content="$t('api.label.dashboardTypeAuto') + (item.scheduleRate ? ` (${dayjs.duration(item.scheduleRate, 'seconds').humanize()})` : '')"
              placement="top"
            >
              <common-icon
                icon="Timer"
                :size="14"
                style="color: var(--el-color-success); margin: 0 4px; cursor: help; align-self: center;"
              />
            </el-tooltip>
            <el-tag
              v-if="item.project?.projectCode"
              size="small"
              type="primary"
              effect="plain"
              class="dashboard-item-tag"
            >
              {{ getProjectName(item) }}
            </el-tag>
            <el-tag
              v-if="item.lastLog"
              size="small"
              :type="item.lastLog.logResult === 'SUCCESS' ? 'success' : 'danger'"
              class="dashboard-item-tag"
            >
              {{ item.lastLog.logResult }}
            </el-tag>
          </div>
          <div class="dashboard-item-sub">
            <span class="dashboard-item-meta">
              {{ $t('api.label.dashboardExecutor') }}: {{ item.modifier || item.creator }}
            </span>
            <span class="dashboard-item-time">
              {{ formatDate(item.modifyDate || item.createDate) }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </el-card>
</template>
