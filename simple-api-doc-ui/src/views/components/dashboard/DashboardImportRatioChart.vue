<script setup>
import { ref, onMounted, inject, watch, computed } from 'vue'
import { getImportTaskRatio } from '@/api/dashboard/DashboardApi'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { storeToRefs } from 'pinia'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import {
  TooltipComponent,
  LegendComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import { useI18n } from 'vue-i18n'

use([
  CanvasRenderer,
  PieChart,
  TooltipComponent,
  LegendComponent
])

const all = inject('dashboard-all', ref(false))
const loading = ref(false)
const { t } = useI18n()
const { isDarkTheme } = storeToRefs(useGlobalConfigStore())

const rawData = ref([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await getImportTaskRatio(all.value)
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
  const borderColor = isDark ? '#1D1E1F' : '#FFFFFF'

  const formattedData = rawData.value.map(item => {
    const name = item.taskType === 'auto'
      ? t('api.label.dashboardTypeAuto')
      : t('api.label.dashboardTypeManual')
    return {
      name,
      value: item.count
    }
  })

  return {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: '8%',
      top: 'center',
      textStyle: {
        color: textColor
      }
    },
    series: [
      {
        name: t('api.label.dashboardImportRatioChart'),
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['40%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 6,
          borderColor,
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold',
            color: textColor
          }
        },
        labelLine: {
          show: false
        },
        data: formattedData.length
          ? formattedData
          : [
              { name: t('api.label.dashboardNoData'), value: 0 }
            ]
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
          <common-icon icon="PieChart" />
          {{ $t('api.label.dashboardImportRatioChart') }}
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
