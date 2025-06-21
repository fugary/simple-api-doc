<script setup>
import { computed, ref } from 'vue'
import { ElButton, ElMessage } from 'element-plus'
import { $i18nBundle } from '@/messages'
import { saveEnvConfigs } from '@/api/ApiProjectApi'
import { defineFormOptions } from '@/components/utils'

const showWindow = ref(false)
const projectInfoItem = ref()
const infoConfig = ref({
  envConfigs: []
})

let callback
const toEditEnvConfigs = (projectInfo) => {
  projectInfoItem.value = projectInfo
  showWindow.value = true
  infoConfig.value.envConfigs = JSON.parse(projectInfo.envContent)
  return new Promise(resolve => (callback = resolve))
}

defineExpose({
  toEditEnvConfigs
})

const saveInfoEnvConfigs = ({ form }) => {
  form.validate(valid => {
    if (valid) {
      saveEnvConfigs(projectInfoItem.value.id, infoConfig.value.envConfigs, { loading: true })
        .then(() => {
          ElMessage.success($i18nBundle('common.msg.saveSuccess'))
          callback?.(projectInfoItem.value)
          showWindow.value = false
        })
    }
  })
  return false
}

const paramsOptions = computed(() => {
  return infoConfig.value.envConfigs.map((config) => {
    return defineFormOptions([{
      labelWidth: '150px',
      labelKey: 'api.label.environmentName',
      prop: 'name',
      colSpan: 8,
      required: true,
      disabled: !config.manual
    }, {
      labelKey: 'api.label.url',
      labelWidth: '130px',
      prop: 'url',
      colSpan: 13,
      required: true,
      pattern: /https?:\/\/.*/,
      disabled: !config.manual
    }])
  })
})

</script>

<template>
  <common-window
    v-model="showWindow"
    show-fullscreen
    width="1000px"
    :ok-label="$t('common.label.save')"
    destroy-on-close
    :ok-click="saveInfoEnvConfigs"
  >
    <template #header>
      <span class="el-dialog__title">
        {{ $t('api.label.environments') }}
      </span>
    </template>
    <common-form
      :model="infoConfig"
      :show-buttons="false"
      class="form-edit-width-100"
    >
      <el-row
        v-for="(item, index) in infoConfig.envConfigs"
        :key="index"
        class="padding-bottom2"
      >
        <template
          v-for="option in paramsOptions[index]"
          :key="`${index}_${option.prop}`"
        >
          <el-col :span="option.colSpan">
            <common-form-control
              label-width="80px"
              :model="item"
              :option="option"
              :prop="`envConfigs.${index}.${option.prop}`"
            />
          </el-col>
        </template>
        <el-col
          :span="3"
          class="padding-left2 padding-top1"
        >
          <el-button
            v-if="index===infoConfig.envConfigs.length-1"
            type="success"
            size="small"
            circle
            @click="infoConfig.envConfigs.push({manual: true})"
          >
            <common-icon icon="Plus" />
          </el-button>
          <el-button
            v-if="item.manual"
            type="danger"
            size="small"
            circle
            @click="infoConfig.envConfigs.splice(index, 1)"
          >
            <common-icon icon="Delete" />
          </el-button>
        </el-col>
      </el-row>
    </common-form>
  </common-window>
</template>

<style scoped>

</style>
