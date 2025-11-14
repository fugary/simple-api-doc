<script setup>
import ApiDocRequestParamsEdit from '@/views/components/api/doc/comp/edit/ApiDocRequestParamsEdit.vue'
import { computed, ref } from 'vue'
import { $i18nKey } from '@/messages'
import { checkAndSaveDocInfoDetail, toParametersSchemaContent } from '@/services/api/ApiDocEditService'
import { cloneDeep } from 'lodash-es'

const showWindow = ref(false)
const apiDocDetail = ref()
const paramsModel = ref()
const toEditApiDocRequestParams = (docDetail) => {
  apiDocDetail.value = docDetail
  showWindow.value = true
}
const emit = defineEmits(['saveComponent'])
const saveSchemaContent = ({ form }) => {
  form.validate(valid => {
    if (valid) {
      console.log('====================schemaData', paramsModel.value)
      const parametersSchema = apiDocDetail.value.parametersSchema = paramsModel.value.parametersSchema
      parametersSchema.schemaContent = toParametersSchemaContent(paramsModel.value)
      checkAndSaveDocInfoDetail(parametersSchema)
        .then((resultData) => emit('saveComponent', resultData))
      showWindow.value = false
    }
  })
  return false
}
const buttons = computed(() => [{
  labelKey: 'common.label.reset',
  type: 'success',
  click: () => toEditApiDocRequestParams(cloneDeep(apiDocDetail.value))
}])
defineExpose({
  toEditApiDocRequestParams
})

</script>

<template>
  <common-window
    v-model="showWindow"
    width="1100px"
    :title="$i18nKey('common.label.commonEdit', 'api.label.queryParams')"
    :ok-label="$t('common.label.save')"
    :ok-click="saveSchemaContent"
    :buttons="buttons"
  >
    <api-doc-request-params-edit
      v-if="apiDocDetail"
      v-model="apiDocDetail"
      v-model:params-model="paramsModel"
    />
  </common-window>
</template>

<style scoped>

</style>
