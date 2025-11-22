<script setup lang="jsx">
import { computed, nextTick, ref, watch } from 'vue'
import { checkParamsFilled } from '@/services/api/ApiDocPreviewService'
import { DEFAULT_HEADERS, SCHEMA_BASE_TYPES, SCHEMA_SELECT_TYPE, SCHEMA_SELECT_TYPES } from '@/consts/ApiConstants'
import { calcApiDocRequestModel, initApiDocParams, processSchemaBeforeSave } from '@/services/api/ApiDocEditService'
import { getSingleSelectOptions } from '@/utils'
import { ElButton, ElLink } from 'element-plus'
import { $i18nBundle, $i18nKey } from '@/messages'
import { calcSuggestionsFunc } from '@/services/api/ApiCommonService'
import { toEditJsonSchema } from '@/utils/DynamicUtils'
import CommonIcon from '@/components/common-icon/index.vue'
import { cloneDeep } from 'lodash-es'

const currentDocModel = defineModel('modelValue', {
  type: Object,
  default: () => ({})
})

const paramsModel = defineModel('paramsModel', {
  type: Object,
  default: () => ({})
})
const currentTabName = ref('requestParamsTab')
const paramList = ['pathParams', 'requestParams', 'headerParams']
const initParamModel = () => {
  paramsModel.value = calcApiDocRequestModel(currentDocModel.value)
  nextTick(() => {
    console.log('=======================params', currentTabName.value, paramsModel.value)
    for (const key of paramList) {
      if (paramsModel.value[key]?.length) {
        currentTabName.value = `${key}Tab`
        break
      }
    }
  })
}

watch(currentDocModel, () => {
  initParamModel()
}, { immediate: true })

const formOptions = computed(() => {
  const basicOptions = [...getSingleSelectOptions(...SCHEMA_BASE_TYPES.filter(type => type !== 'object')),
    ...SCHEMA_SELECT_TYPES.filter(type => type.value !== SCHEMA_SELECT_TYPE.BASIC).map(item => ({
      value: item.value,
      label: $i18nBundle(item.labelKey)
    }))]
  const getToEditFunc = model => {
    return () => {
      const newModel = cloneDeep(model)
      initApiDocParams([newModel])
      return toEditJsonSchema(newModel, paramsModel.value.parametersSchema, {
        onSaveJsonSchema (toSaveData) {
          let __type = toSaveData.type
          if (toSaveData.dataType === SCHEMA_SELECT_TYPE.REF || toSaveData.dataType === SCHEMA_SELECT_TYPE.XXX_OF) {
            __type = toSaveData.dataType
          }
          Object.assign(model, {
            name: toSaveData.name,
            schema: toSaveData.schema,
            required: toSaveData.required,
            description: toSaveData.schema?.description,
            __type
          })
        }
      })
    }
  }
  return [{
    labelKey: 'common.label.name',
    required: true,
    prop: 'name',
    minWidth: '150px',
    dynamicOption: (model) => {
      return {
        type: model.in === 'header' ? 'autocomplete' : 'input',
        attrs: {
          fetchSuggestions: calcSuggestionsFunc(DEFAULT_HEADERS),
          triggerOnFocus: false
        },
        slots: {
          append () {
            console.log('============model', model, paramsModel.value)
            return <ElLink type="primary" onClick={getToEditFunc(model)}>
              <CommonIcon icon="Edit"/>
            </ElLink>
          }
        }
      }
    }
  }, {
    labelKey: 'api.label.type',
    prop: '__type',
    type: 'select-v2',
    required: true,
    attrs: {
      clearable: false,
      filterable: true,
      options: basicOptions
    },
    formChange (type, { model }) {
      console.log('================model', model)
      if (type === SCHEMA_SELECT_TYPE.REF || type === SCHEMA_SELECT_TYPE.XXX_OF) {
        model.dataType = type
        getToEditFunc(model)()
      } else if (model.schema) {
        model.dataType = SCHEMA_SELECT_TYPE.BASIC
        model.type = type
        processSchemaBeforeSave(model)
      }
    },
    minWidth: '100px'
  }, {
    labelKey: 'api.label.required',
    prop: 'required',
    type: 'switch',
    minWidth: '100px'
  }, {
    labelKey: 'common.label.description',
    prop: 'description',
    minWidth: '300px'
  }]
})
const addParamItem = key => {
  if (paramsModel.value[key]) {
    paramsModel.value[key].push({
      in: key === 'headerParams' ? 'header' : 'query',
      style: 'form',
      explode: true,
      schema: {
        type: 'string'
      }
    })
  }
}
const deleteParam = (key, { index }) => {
  if (paramsModel.value[key]) {
    paramsModel.value[key].splice(index, 1)
  }
}

</script>

<template>
  <common-form
    :model="paramsModel"
    class="form-edit-width-100"
    :show-buttons="false"
  >
    <el-tabs
      v-model="currentTabName"
      class="form-edit-width-100 common-tabs"
    >
      <el-tab-pane
        v-if="paramsModel.pathParams?.length"
        name="pathParamsTab"
      >
        <template #label>
          <el-badge
            :type="checkParamsFilled(paramsModel.pathParams) ? 'primary' : 'danger'"
            :value="paramsModel.pathParams?.length"
            :show-zero="false"
          >
            {{ $t('api.label.pathParams') }}
          </el-badge>
        </template>
        <common-table-form
          :form-options="formOptions"
          :model="paramsModel"
          data-list-key="pathParams"
          :show-operation="false"
        />
      </el-tab-pane>
      <el-tab-pane name="requestParamsTab">
        <template #label>
          <el-badge
            :type="checkParamsFilled(paramsModel.requestParams) ? 'primary' : 'danger'"
            :value="paramsModel.requestParams?.length"
            :show-zero="false"
          >
            {{ $t('api.label.queryParams') }}
          </el-badge>
        </template>
        <common-table-form
          :form-options="formOptions"
          :model="paramsModel"
          data-list-key="requestParams"
          @delete="deleteParam('requestParams', $event)"
        />
        <el-container class="margin-top1">
          <el-button
            type="primary"
            size="small"
            @click="addParamItem('requestParams')"
          >
            <common-icon
              class="margin-right1"
              icon="Plus"
            />
            {{ $i18nKey('common.label.commonAdd1', 'api.label.queryParams') }}
          </el-button>
        </el-container>
      </el-tab-pane>
      <el-tab-pane name="headerParamsTab">
        <template #label>
          <el-badge
            :type="checkParamsFilled(paramsModel.headerParams) ? 'primary' : 'danger'"
            :value="paramsModel.headerParams?.length"
            :show-zero="false"
          >
            {{ $t('api.label.requestHeaders') }}
          </el-badge>
        </template>
        <common-table-form
          :form-options="formOptions"
          :model="paramsModel"
          data-list-key="headerParams"
          @delete="deleteParam('headerParams', $event)"
        />
        <el-container class="margin-top1">
          <el-button
            type="primary"
            size="small"
            @click="addParamItem('headerParams')"
          >
            <common-icon
              class="margin-right1"
              icon="Plus"
            />
            {{ $i18nKey('common.label.commonAdd1', 'api.label.requestHeaders') }}
          </el-button>
        </el-container>
      </el-tab-pane>
    </el-tabs>
    <!--    {{ paramsModel.requestParams }}-->
  </common-form>
</template>

<style scoped>

</style>
