<script setup>
import { computed, provide } from 'vue'
import { useGlobalSaveSearchParam } from '@/hooks/CommonHooks'
import DashboardMetrics from './components/dashboard/DashboardMetrics.vue'
import DashboardTrendChart from './components/dashboard/DashboardTrendChart.vue'
import DashboardProjectActivity from './components/dashboard/DashboardProjectActivity.vue'
import DashboardRecentShares from './components/dashboard/DashboardRecentShares.vue'
import DashboardRecentImports from './components/dashboard/DashboardRecentImports.vue'

const { searchParam, saveSearchParam } = useGlobalSaveSearchParam({ all: false })
const all = computed({
  get: () => !!searchParam.value.all,
  set: (value) => {
    searchParam.value.all = !!value
    saveSearchParam()
  }
})

provide('dashboard-all', all)
</script>

<template>
  <el-container class="flex-column dashboard-container">
    <div class="dashboard-header">
      <el-radio-group v-model="all">
        <el-radio-button :value="false">
          {{ $t('api.label.dashboardMyData') }}
        </el-radio-button>
        <el-radio-button :value="true">
          {{ $t('api.label.dashboardAllData') }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <dashboard-metrics />

    <el-row
      :gutter="20"
      class="margin-top3"
    >
      <el-col :span="24">
        <dashboard-trend-chart />
      </el-col>
    </el-row>

    <el-row
      :gutter="20"
      class="margin-top3"
    >
      <el-col :span="8">
        <dashboard-project-activity />
      </el-col>
      <el-col :span="8">
        <dashboard-recent-shares />
      </el-col>
      <el-col :span="8">
        <dashboard-recent-imports />
      </el-col>
    </el-row>
  </el-container>
</template>

<style scoped>
.dashboard-container {
  padding: 0;
}
.dashboard-header {
  margin-bottom: 20px;
  display: flex;
  justify-content: flex-end;
}
.margin-top3 {
  margin-top: 20px;
}
</style>
