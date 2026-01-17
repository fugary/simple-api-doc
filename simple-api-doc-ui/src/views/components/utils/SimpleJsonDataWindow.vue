<script setup lang="js">
import { ref } from 'vue'
import SimpleJsonDataTable from '@/views/components/utils/SimpleJsonDataTable.vue'
import { xml2Json } from '@/api/ApiProjectApi'
import { isXml } from '@/services/api/ApiCommonService'
import { cloneDeep } from 'lodash-es'
const showWindow = ref(false)
defineProps({
  title: {
    type: String,
    default: ''
  },
  theme: {
    type: String,
    default: undefined
  },
  manual: {
    type: Boolean,
    default: false
  }
})
const vModel = defineModel({ type: String, default: '' })
const tableConfig = defineModel('tableConfig', { type: Object, default: () => ({}) })
defineEmits(['update:tableConfig'])

const formModel = ref({})
const xmlContent = ref()
const showJsonDataWindow = async (data) => {
  xmlContent.value = null
  if (isXml(data)) {
    xmlContent.value = data
    data = await xml2Json({ keyword: data }).then(data => data.resultData)
  }
  vModel.value = data
  formModel.value = cloneDeep(tableConfig.value)
  showWindow.value = true
}
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
      :theme="theme"
      :manual="manual"
      :xml-content="xmlContent"
      @save-table-config="tableConfig=cloneDeep(formModel)"
    />
  </common-window>
</template>

<style scoped>

</style>
