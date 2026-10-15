<script setup>
import { ref, onMounted, inject, watch, computed } from 'vue'
import { getTrend } from '@/api/dashboard/DashboardApi'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { storeToRefs } from 'pinia'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import {
  TooltipComponent,
  LegendComponent,
  GridComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import { useI18n } from 'vue-i18n'

use([
  CanvasRenderer,
  LineChart,
  TooltipComponent,
  LegendComponent,
  GridComponent
])

const all = inject('dashboard-all', ref(false))
const loading = ref(false)
const { t } = useI18n()
const { isDarkTheme } = storeToRefs(useGlobalConfigStore())

const trendData = ref({ dates: [], docs: [], projects: [] })

const loadTrend = async () => {
  loading.value = true
  try {
    const res = await getTrend(all.value, 30)
    if (res && res.success) {
      trendData.value = res.resultData || { dates: [], docs: [], projects: [] }
    }
  } finally {
    loading.value = false
  }
}

const chartOption = computed(() => {
  const isDark = isDarkTheme.value
  const textColor = isDark ? '#E5EAF3' : '#606266'
  const splitLineColor = isDark ? '#363637' : '#E4E7ED'

  return {
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: [t('api.label.dashboardTrendProjects'), t('api.label.dashboardTrendApis')],
      top: 0,
      right: 0,
      textStyle: {
        color: textColor
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '12%',
      outerBoundsMode: 'same',
      outerBoundsContain: 'axisLabel'
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trendData.value.dates,
      axisLabel: {
        color: textColor
      }
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: textColor
      },
      splitLine: {
        lineStyle: {
          type: 'dashed',
          color: splitLineColor
        }
      }
    },
    series: [
      {
        name: t('api.label.dashboardTrendProjects'),
        type: 'line',
        smooth: true,
        data: trendData.value.projects,
        itemStyle: { color: '#409EFF' },
        lineStyle: { width: 3 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [{
              offset: 0, color: 'rgba(64,158,255,0.3)'
            }, {
              offset: 1, color: 'rgba(64,158,255,0.1)'
            }]
          }
        }
      },
      {
        name: t('api.label.dashboardTrendApis'),
        type: 'line',
        smooth: true,
        data: trendData.value.docs,
        itemStyle: { color: '#67C23A' },
        lineStyle: { width: 3 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [{
              offset: 0, color: 'rgba(103,194,58,0.3)'
            }, {
              offset: 1, color: 'rgba(103,194,58,0.1)'
            }]
          }
        }
      }
    ]
  }
})

onMounted(() => {
  loadTrend()
})

watch(all, () => {
  loadTrend()
})
</script>

<template>
  <el-card
    shadow="hover"
    style="border-radius: 8px; height: 100%;"
  >
    <template #header>
      <div class="card-header">
        <span class="card-title">
          <common-icon icon="TrendCharts" />
          {{ $t('api.label.dashboardTrendChart') }}
        </span>
      </div>
    </template>
    <div
      v-loading="loading"
      class="chart-container"
    >
      <v-chart
        class="chart"
        :option="chartOption"
        autoresize
      />
    </div>
  </el-card>
</template>

<style scoped>
.chart-container {
  height: 340px;
  width: 100%;
}

.chart {
  width: 100%;
  height: 100%;
}
</style>
