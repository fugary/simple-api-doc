<script setup>
import { ref, onMounted, inject, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getRecentShares } from '@/api/dashboard/DashboardApi'
import { formatDate, formatDay } from '@/utils'

const router = useRouter()
const all = inject('dashboard-all', ref(false))
const loading = ref(false)
const recentShares = ref([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await getRecentShares(all.value)
    if (res && res.success) {
      recentShares.value = res.resultData || []
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

const getShareUrl = (share) => {
  const routeLocation = { name: 'ShareDoc', params: { shareId: share.shareId || share.id } }
  if (share.sharePassword) {
    routeLocation.query = { pwd: share.sharePassword }
  }
  return router.resolve(routeLocation).href
}

const getShareDisplayName = (item) => {
  return item.shareName?.trim() || item.project?.projectName?.trim() || item.projectCode
}

const getShareProjectName = (item) => {
  return item.project?.projectName?.trim() || item.project?.projectCode
}

const showShareProjectTag = (item) => {
  return !!item.project?.projectCode
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
          <common-icon icon="Share" />
          {{ $t('api.label.dashboardRecentShares') }}
        </span>
        <el-link
          type="primary"
          :underline="false"
          @click="router.push({ name: 'AdminProjectShares' })"
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
        v-if="!recentShares.length"
        :description="$t('api.label.dashboardNoData')"
        :image-size="60"
      />
      <div
        v-for="item in recentShares"
        :key="item.id"
        class="activity-item"
      >
        <el-avatar
          :size="36"
          style="background-color: var(--el-color-success); flex-shrink: 0; margin-right: 12px;"
        >
          <common-icon
            icon="Share"
            :size="18"
          />
        </el-avatar>

        <div class="dashboard-item-main">
          <div class="dashboard-item-header">
            <el-link
              tag="b"
              truncated
              :underline="false"
              class="dashboard-item-title"
              :href="getShareUrl(item)"
              target="_blank"
            >
              {{ getShareDisplayName(item) }}
            </el-link>
            <el-tooltip
              v-if="item.sharePassword"
              :content="$t('api.label.hasPassword')"
              placement="top"
            >
              <common-icon
                icon="Lock"
                :size="14"
                style="color: var(--el-color-warning); margin: 0 4px; cursor: help; align-self: center;"
              />
            </el-tooltip>
            <el-tag
              v-if="showShareProjectTag(item)"
              size="small"
              type="primary"
              effect="plain"
              class="dashboard-item-tag"
            >
              {{ getShareProjectName(item) }}
            </el-tag>
          </div>
          <div class="dashboard-item-sub">
            <span class="dashboard-item-meta">
              {{ $t('api.label.dashboardCreator') }}: {{ item.userName || item.creator }}
              · {{ item.expireDate ? formatDay(item.expireDate) : $t('api.label.dashboardPermanent') }}
            </span>
            <span class="dashboard-item-time">
              {{ formatDate(item.createDate) }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </el-card>
</template>
