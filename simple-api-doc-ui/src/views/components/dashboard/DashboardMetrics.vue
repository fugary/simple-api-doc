<script setup>
import { ref, onMounted, inject, watch } from 'vue'
import { getMetrics } from '@/api/dashboard/DashboardApi'
import { useRouter } from 'vue-router'

const metrics = ref({
  projectCount: 0,
  apiCount: 0,
  userCount: 0,
  groupCount: 0,
  shareCount: 0,
  aiCacheCount: 0
})

const router = useRouter()
const all = inject('dashboard-all', ref(false))
const loading = ref(false)

const openPage = (name) => {
  if (name) {
    router.push({ name })
  }
}

const loadMetrics = async () => {
  loading.value = true
  try {
    const res = await getMetrics(all.value)
    if (res && res.success) {
      metrics.value = res.resultData
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadMetrics()
})

watch(all, () => {
  loadMetrics()
})
</script>

<template>
  <el-row
    v-loading="loading"
    :gutter="20"
    class="metric-row"
  >
    <el-col :span="6">
      <el-card
        shadow="hover"
        class="metric-card metric-card--interactive bg-primary"
        @click="openPage('ApiProjects')"
      >
        <div class="metric-content">
          <div class="metric-info">
            <div class="metric-title">
              {{ $t('api.label.dashboardProjectCount') }}
            </div>
            <div class="metric-value">
              {{ metrics.projectCount }}
            </div>
          </div>
          <div class="metric-icon">
            <common-icon
              icon="FolderOpened"
              class="icon-svg"
            />
          </div>
        </div>
      </el-card>
    </el-col>

    <el-col :span="6">
      <el-card
        shadow="hover"
        class="metric-card metric-card--interactive bg-purple"
        @click="openPage('ApiProjects')"
      >
        <div class="metric-content">
          <div class="metric-info">
            <div class="metric-title">
              {{ $t('api.label.dashboardApiAndDocCount') }}
            </div>
            <div class="metric-value">
              {{ metrics.apiCount }}
            </div>
          </div>
          <div class="metric-icon">
            <common-icon
              icon="Document"
              class="icon-svg"
            />
          </div>
        </div>
      </el-card>
    </el-col>

    <el-col :span="6">
      <el-card
        shadow="hover"
        class="metric-card metric-card--interactive bg-success"
        @click="openPage('AdminProjectShares')"
      >
        <div class="metric-content">
          <div class="metric-info">
            <div class="metric-title">
              {{ $t('api.label.dashboardShareCount') }}
            </div>
            <div class="metric-value">
              {{ metrics.shareCount }}
            </div>
          </div>
          <div class="metric-icon">
            <common-icon
              icon="Share"
              class="icon-svg"
            />
          </div>
        </div>
      </el-card>
    </el-col>

    <el-col :span="6">
      <el-card
        shadow="hover"
        class="metric-card metric-card--interactive bg-warning"
        @click="openPage('AiCaches')"
      >
        <div class="metric-content">
          <div class="metric-info">
            <div class="metric-title">
              {{ $t('api.label.dashboardAiCacheCount') }}
            </div>
            <div class="metric-value">
              {{ metrics.aiCacheCount }}
            </div>
          </div>
          <div class="metric-icon">
            <common-icon
              icon="Cpu"
              class="icon-svg"
            />
          </div>
        </div>
      </el-card>
    </el-col>
  </el-row>
</template>

<style scoped>
.metric-row {
  display: flex;
  align-items: stretch;
}

.metric-row > .el-col {
  display: flex;
  flex-direction: column;
}

.metric-card {
  border-radius: 12px;
  border: none;
  color: white;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  overflow: hidden;
  position: relative;
  flex: 1;
  height: 100%;
}

.metric-card--interactive {
  cursor: pointer;
}

.metric-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 12px 24px rgba(0,0,0,0.15);
}

.metric-card :deep(.el-card__body) {
  padding: 20px;
  height: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.metric-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  z-index: 2;
  width: 100%;
}

.metric-info {
  display: flex;
  flex-direction: column;
  width: calc(100% - 66px); /* 100% minus icon width + gap */
}

.metric-title {
  font-size: 14px;
  opacity: 0.9;
  margin-bottom: 8px;
  font-weight: 500;
  letter-spacing: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.metric-value {
  font-size: 34px;
  font-weight: bold;
  font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
  text-shadow: 0 2px 4px rgba(0,0,0,0.1);
  line-height: 1;
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
}

.metric-icon {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(4px);
}

.icon-svg {
  font-size: 32px;
  opacity: 0.9;
}

.bg-primary {
  background: linear-gradient(135deg, #409EFF 0%, #79bbff 100%);
}
.bg-purple {
  background: linear-gradient(135deg, #B37FEB 0%, #D3ADF7 100%);
}
.bg-success {
  background: linear-gradient(135deg, #67C23A 0%, #95d475 100%);
}
.bg-warning {
  background: linear-gradient(135deg, #E6A23C 0%, #eebe77 100%);
}

.metric-card::after {
  content: '';
  position: absolute;
  top: -30px;
  right: -30px;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  z-index: 1;
}
</style>
