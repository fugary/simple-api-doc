<script setup>
import { ref, onMounted, inject, watch, computed } from 'vue'
import { getProjectShareRatio } from '@/api/dashboard/DashboardApi'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { storeToRefs } from 'pinia'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import {
  TooltipComponent,
  GridComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import { useI18n } from 'vue-i18n'

use([
  CanvasRenderer,
  BarChart,
  TooltipComponent,
  GridComponent
])

const all = inject('dashboard-all', ref(false))
const loading = ref(false)
const { t } = useI18n()
const { isDarkTheme } = storeToRefs(useGlobalConfigStore())

const rawData = ref([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await getProjectShareRatio(all.value)
    if (res && res.success) {
      rawData.value = res.resultData || []
    }
  } finally {
    loading.value = false
  }
}

const chartOption = computed(() => {
  const isDark = isDarkTheme.value
  const textColor = isDark ? '#E5EAF3' : '#606266'
  const splitLineColor = isDark ? '#363637' : '#E4E7ED'

  const projectNames = rawData.value.map(item => item.projectName || t('api.label.dashboardUnnamedProject'))
  const counts = rawData.value.map(item => item.count)

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      },
      formatter: (params) => {
        if (!params || !params.length) return ''
        const p = params[0]
        return `${p.name}<br/>${t('api.label.dashboardShareCount')}: <b>${p.value}</b>`
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '24%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: projectNames,
      axisLabel: {
        interval: 0,
        rotate: projectNames.length > 5 ? 25 : 0,
        color: textColor,
        formatter: (val) => {
          return val && val.length > 12 ? `${val.substring(0, 12)}...` : val
        }
      }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
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
        name: t('api.label.dashboardShareCount'),
        type: 'bar',
        data: counts,
        itemStyle: {
          color: '#409EFF',
          borderRadius: [4, 4, 0, 0]
        },
        barMaxWidth: 32
      }
    ]
  }
})

onMounted(() => {
  loadData()
})

watch(all, () => {
  loadData()
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
          <common-icon icon="DataAnalysis" />
          {{ $t('api.label.dashboardProjectShareChart') }}
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
  height: 280px;
  width: 100%;
}

.chart {
  width: 100%;
  height: 100%;
}
</style>
