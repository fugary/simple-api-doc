<script setup>
import { ref, onMounted, inject, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getRecentProjects } from '@/api/dashboard/DashboardApi'
import { formatDate } from '@/utils'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const { t } = useI18n()
const all = inject('dashboard-all', ref(false))
const loading = ref(false)
const recentProjects = ref([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await getRecentProjects(all.value)
    if (res && res.success) {
      recentProjects.value = res.resultData || []
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

const openProject = (project) => {
  router.push({
    name: 'ApiProjectDetail',
    params: { projectCode: project.projectCode }
  })
}

const getProjectName = (item) => {
  const name = item.projectName?.trim()
  if (!name || name === item.projectCode) {
    return t('api.label.dashboardUnnamedProject')
  }
  return name
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
          <common-icon icon="Monitor" />
          {{ $t('api.label.dashboardRecentProjects') }}
        </span>
        <el-link
          type="primary"
          :underline="false"
          @click="router.push({ name: 'ApiProjects' })"
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
        v-if="!recentProjects.length"
        :description="$t('api.label.dashboardNoData')"
        :image-size="60"
      />
      <div
        v-for="item in recentProjects"
        :key="item.id"
        class="activity-item"
        @click="openProject(item)"
      >
        <el-avatar
          :size="36"
          style="background-color: var(--el-color-primary); flex-shrink: 0; margin-right: 12px;"
        >
          <common-icon
            icon="FolderOpened"
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
              {{ getProjectName(item) }}
            </el-text>
          </div>
          <div class="dashboard-item-sub">
            <span class="dashboard-item-meta">
              {{ $t('api.label.dashboardCreator') }}: {{ item.userName }}
              <template v-if="item.modifier && item.modifier !== item.userName">
                · {{ $t('api.label.dashboardModifier') }}: {{ item.modifier }}
              </template>
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
