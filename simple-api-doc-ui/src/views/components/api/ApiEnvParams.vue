<script setup>
import { ref } from 'vue'
import { cloneDeep } from 'lodash-es'
import ApiProjectApi from '@/api/ApiProjectApi'
import CommonParamsEdit from '@/views/components/utils/CommonParamsEdit.vue'
import { ElMessage } from 'element-plus'
import { $i18nBundle } from '@/messages'
import { DEFAULT_HEADERS } from '@/consts/ApiConstants'
import { defineFormOptions } from '@/components/utils'

const extractRulesOptions = defineFormOptions([{
  label: '',
  prop: 'enabled',
  type: 'switch',
  value: true,
  width: 60,
  columnAttrs: {
    align: 'center'
  }
}, {
  labelKey: 'api.label.matchPath',
  prop: 'apiPath',
  tooltipKey: 'api.msg.matchPathTip',
  minWidth: 150
}, {
  labelKey: 'api.label.extractPath',
  prop: 'jsonPath',
  required: true,
  tooltipKey: 'api.msg.extractPathTip',
  minWidth: 180,
  headerSlot: 'jsonPathHeader'
}])

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
      groupConfig.value = { envParams: [], ...JSON.parse(projectItem.value.groupConfig) }
    } else {
      groupConfig.value = { envParams: [] }
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
      const configToSave = cloneDeep(groupConfig.value)
      configToSave.envParams?.forEach(param => {
        delete param.showExtractRules
      })
      projectItem.value.groupConfig = JSON.stringify(configToSave)
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
    width="900px"
  >
    <template #header>
      <span class="el-dialog__title">
        {{ $t('api.label.envAndExtractConfig') }}
      </span>
    </template>
    <common-form
      :model="groupConfig"
      :show-buttons="false"
      class="form-edit-width-100"
    >
      <div class="margin-bottom2">
        <common-params-edit
          v-model="groupConfig.envParams"
          form-prop="envParams"
          name-required
          :name-suggestions="DEFAULT_HEADERS"
        >
          <template #item="{ item, index }">
            <div style="margin-left: 20px; margin-bottom: 8px; display: flex; align-items: center; gap: 12px;">
              <el-button
                type="primary"
                link
                size="small"
                @click="item.showExtractRules = !item.showExtractRules"
              >
                {{ item.showExtractRules ? $t('api.label.collapseExtractRules') : $t('api.label.configExtractRules') }}
                <span
                  v-if="item.extractRules?.length"
                  style="margin-left: 4px; margin-right: 2px"
                >({{ item.extractRules.length }})</span>
                <common-icon
                  :icon="item.showExtractRules ? 'ArrowDown' : 'ArrowRight'"
                  style="margin-left: 2px"
                />
              </el-button>

              <el-button
                v-if="item.showExtractRules"
                type="primary"
                size="small"
                link
                @click="(item.extractRules = item.extractRules || []).push({ enabled: true })"
              >
                <common-icon icon="Plus" /> {{ $t('api.label.addExtractRule') }}
              </el-button>
            </div>

            <el-collapse-transition>
              <div
                v-show="item.showExtractRules"
                style="padding: 12px; margin-bottom: 12px; background-color: var(--el-fill-color-light); margin-left: 20px; margin-right: 20px; border-radius: 4px; border: 1px solid var(--el-border-color-lighter);"
              >
                <common-table-form
                  :model="item"
                  data-list-key="extractRules"
                  :form-options="extractRulesOptions"
                  :form-prop-prefix="`envParams.${index}`"
                  @delete="({index: idx}) => item.extractRules.splice(idx, 1)"
                >
                  <template #jsonPathHeader>
                    <div style="display: flex; align-items: center;">
                      <span style="color: var(--el-color-danger); margin-right: 4px;">*</span>
                      <span>{{ $t('api.label.extractPathFull') }}</span>
                    </div>
                  </template>
                </common-table-form>
              </div>
            </el-collapse-transition>
          </template>
        </common-params-edit>
      </div>
    </common-form>
  </common-window>
</template>

<style scoped>

</style>
