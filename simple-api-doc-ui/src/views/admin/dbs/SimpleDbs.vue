<script setup lang="jsx">
import { computed, onMounted } from 'vue'
import { useTableAndSearchForm } from '@/hooks/CommonHooks'
import ApiDbApi from '@/api/ApiDbApi'

const { tableData, loading, searchMethod: loadApiDbs } = useTableAndSearchForm({
  searchMethod: ApiDbApi.search
})
onMounted(() => loadApiDbs())
const columns = computed(() => {
  return [{
    label: 'DB Url',
    prop: 'url',
    minWidth: '300px'
  }, {
    label: 'Max',
    prop: 'maxPoolSize'
  }, {
    label: 'Total',
    prop: 'totalCount'
  }, {
    label: 'Active',
    prop: 'activeCount'
  }, {
    label: 'Idle',
    prop: 'idleCount'
  }, {
    label: 'User',
    prop: 'userName'
  }]
})
</script>

<template>
  <el-container class="flex-column">
    <common-table
      :data="tableData"
      :columns="columns"
      :loading="loading"
    />
  </el-container>
</template>

<style scoped>

</style>
