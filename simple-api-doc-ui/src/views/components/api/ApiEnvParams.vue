<script setup>
import { ref } from 'vue'
import ApiProjectApi from '@/api/ApiProjectApi'
import CommonParamsEdit from '@/views/components/utils/CommonParamsEdit.vue'
import { ElMessage } from 'element-plus'
import { $i18nBundle } from '@/messages'
import { DEFAULT_HEADERS } from '@/consts/ApiConstants'

const showWindow = ref(false)
const projectItem = ref()
const groupConfig = ref({
  envParams: []
})

let callback
const toEditGroupEnvParams = (projectId) => {
  ApiProjectApi.getById(projectId, { loading: true }).then(data => {
    projectItem.value = data.resultData
    if (projectItem.value?.groupConfig) {
      groupConfig.value = JSON.parse(projectItem.value.groupConfig)
    }
    showWindow.value = true
  })
  return new Promise(resolve => (callback = resolve))
}

defineExpose({
  toEditGroupEnvParams
})

const saveGroupConfig = ({ form }) => {
  form.validate(valid => {
    if (valid) {
      projectItem.value.groupConfig = JSON.stringify({ ...groupConfig.value })
      ApiProjectApi.saveOrUpdate(projectItem.value)
        .then(() => {
          ElMessage.success($i18nBundle('common.msg.saveSuccess'))
          callback?.(projectItem.value)
          showWindow.value = false
        })
    }
  })
  return false
}

</script>

<template>
  <common-window
    v-model="showWindow"
    :ok-label="$t('common.label.save')"
    destroy-on-close
    :ok-click="saveGroupConfig"
  >
    <template #header>
      <span class="el-dialog__title">
        {{ $t('api.label.mockEnv') }}
      </span>
    </template>
    <common-form
      :model="groupConfig"
      :show-buttons="false"
      class="form-edit-width-100"
    >
      <common-params-edit
        v-model="groupConfig.envParams"
        form-prop="envParams"
        name-required
        value-required
        :name-suggestions="DEFAULT_HEADERS"
      />
    </common-form>
  </common-window>
</template>

<style scoped>

</style>
