<script setup>
import { ref } from 'vue'
import ApiProjectApi from '@/api/ApiProjectApi'
import CommonParamsEdit from '@/views/components/utils/CommonParamsEdit.vue'
import { ElMessage } from 'element-plus'
import { $i18nBundle } from '@/messages'
import { DEFAULT_HEADERS } from '@/consts/ApiConstants'
import { defineFormOptions } from '@/components/utils'
import { getExtractedEnvParams } from '@/services/api/ApiCommonService'

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
const cachedParams = ref([])
const getCachedValue = (name) => {
  if (!name) return null
  return cachedParams.value.find(p => p.name === name)?.value
}

let callback
const toEditGroupEnvParams = (projectId) => {
  cachedParams.value = getExtractedEnvParams(projectId)
  ApiProjectApi.getById(projectId, { loading: true }).then(data => {
    projectItem.value = data.resultData
    if (projectItem.value?.groupConfig) {
      groupConfig.value = { envParams: [], ...JSON.parse(projectItem.value.groupConfig) }
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
          <template #item="{ item }">
            <div style="margin-left: 20px; margin-bottom: 8px; display: flex; align-items: center; gap: 12px;">
              <el-button
                type="primary"
                link
                size="small"
                @click="item.showExtractRules = !item.showExtractRules"
              >
                <common-icon :icon="item.showExtractRules ? 'ArrowUp' : 'ArrowDown'" />
                {{ item.showExtractRules ? $t('api.label.collapseExtractRules') : $t('api.label.configExtractRules') }}
                <span
                  v-if="item.extractRules?.length"
                  style="margin-left: 4px;"
                >({{ item.extractRules.length }})</span>
              </el-button>

              <el-tooltip
                v-if="getCachedValue(item.name)"
                :content="$t('api.msg.successExtractCache', [getCachedValue(item.name)])"
                placement="top"
                max-width="400px"
              >
                <span style="font-size: 12px; color: var(--el-color-success); cursor: pointer; display: inline-flex; align-items: center; gap: 4px;">
                  <common-icon icon="InfoFilled" /> {{ $t('api.label.cachedValue') }}
                </span>
              </el-tooltip>
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
                  @delete="({index}) => item.extractRules.splice(index, 1)"
                >
                  <template #jsonPathHeader>
                    <div style="display: flex; align-items: center;">
                      <span style="color: var(--el-color-danger); margin-right: 4px;">*</span>
                      <span>{{ $t('api.label.extractPathFull') }}</span>
                    </div>
                  </template>
                </common-table-form>
                <div style="margin-top: 8px;">
                  <el-button
                    type="primary"
                    size="small"
                    plain
                    @click="(item.extractRules = item.extractRules || []).push({ enabled: true })"
                  >
                    <common-icon icon="Plus" /> {{ $t('api.label.addExtractRule') }}
                  </el-button>
                </div>
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
