<script setup>
import { ref, onMounted, inject, watch } from 'vue'
import { getTrend } from '@/api/dashboard/DashboardApi'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import { useI18n } from 'vue-i18n'

use([
  CanvasRenderer,
  LineChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
])

const all = inject('dashboard-all', ref(false))
const loading = ref(false)
const { t } = useI18n()

const chartOption = ref({})

const loadTrend = async () => {
  loading.value = true
  try {
    const res = await getTrend(all.value, 30)
    if (res && res.success) {
      const data = res.resultData

      chartOption.value = {
        title: {
          text: t('api.label.dashboardTrendChart'),
          left: 'left',
          textStyle: {
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        tooltip: {
          trigger: 'axis'
        },
        legend: {
          data: [t('api.label.dashboardTrendProjects'), t('api.label.dashboardTrendApis')],
          top: 0,
          right: 0
        },
        grid: {
          left: '3%',
          right: '4%',
          bottom: '3%',
          containLabel: true
        },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: data.dates
        },
        yAxis: {
          type: 'value',
          splitLine: {
            lineStyle: {
              type: 'dashed',
              color: 'var(--el-border-color-lighter)'
            }
          }
        },
        series: [
          {
            name: t('api.label.dashboardTrendProjects'),
            type: 'line',
            smooth: true,
            data: data.projects,
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
            data: data.docs,
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
    }
  } finally {
    loading.value = false
  }
}

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
  height: 380px;
  width: 100%;
}

.chart {
  width: 100%;
  height: 100%;
}
</style>
