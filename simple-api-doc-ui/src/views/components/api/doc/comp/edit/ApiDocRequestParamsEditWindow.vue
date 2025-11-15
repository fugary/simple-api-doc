<script setup>
import ApiDocRequestParamsEdit from '@/views/components/api/doc/comp/edit/ApiDocRequestParamsEdit.vue'
import { computed, ref } from 'vue'
import { $i18nBundle, $i18nKey } from '@/messages'
import { checkAndSaveDocInfoDetail, toParametersSchemaContent } from '@/services/api/ApiDocEditService'
import { cloneDeep } from 'lodash-es'
import { $coreConfirm } from '@/utils'
import ApiProjectInfoDetailApi from '@/api/ApiProjectInfoDetailApi'

const showWindow = ref(false)
const apiDocDetail = ref()
const paramsModel = ref()
const toEditApiDocRequestParams = (docDetail) => {
  apiDocDetail.value = docDetail
  showWindow.value = true
}
const emit = defineEmits(['saveComponent', 'deleteComponent'])
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
}, {
  labelKey: 'common.label.delete',
  type: 'danger',
  click () {
    return $coreConfirm($i18nBundle('common.msg.deleteConfirm')).then(() => {
      return ApiProjectInfoDetailApi.deleteById(paramsModel.value.parametersSchema.id)
        .then(() => {
          emit('deleteComponent', paramsModel.value.parametersSchema)
          showWindow.value = false
        })
    })
  },
  enabled: !!paramsModel.value?.parametersSchema?.id
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
