<script setup lang="js">
import { ref, watch } from 'vue'
import SimpleJsonDataTable from '@/views/components/utils/SimpleJsonDataTable.vue'
import { xml2Json } from '@/api/ApiProjectApi'
import { isXml } from '@/services/api/ApiCommonService'
import { cloneDeep } from 'lodash-es'
const showWindow = ref(false)
defineProps({
  title: {
    type: String,
    default: ''
  }
})
const vModel = defineModel({ type: String, default: '' })
const tableConfig = defineModel('tableConfig', { type: Object, default: () => ({}) })

const formModel = ref({})
const xmlContent = ref()
const showJsonDataWindow = async (data) => {
  formModel.value = cloneDeep(tableConfig.value)
  xmlContent.value = null
  if (isXml(data)) {
    xmlContent.value = data
    data = await xml2Json({ keyword: data }).then(data => data.resultData)
  }
  vModel.value = data
  showWindow.value = true
}
watch(formModel, () => {
  tableConfig.value = formModel.value
}, { deep: true })
defineExpose({
  showJsonDataWindow
})
</script>

<template>
  <common-window
    v-model="showWindow"
    width="1100px"
    :show-cancel="false"
    :ok-label="$t('common.label.close')"
    destroy-on-close
    :title="title||$t('api.label.viewAsTable')"
    append-to-body
    show-fullscreen
    v-bind="$attrs"
  >
    <simple-json-data-table
      v-model:table-config="formModel"
      v-model="vModel"
      :xml-content="xmlContent"
    />
  </common-window>
</template>

<style scoped>

</style>
