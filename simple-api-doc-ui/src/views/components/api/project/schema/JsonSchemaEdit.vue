<script setup lang="jsx">
import { computed, reactive, ref } from 'vue'
import {
  SCHEMA_BASE_TYPES,
  SCHEMA_BASIC_TYPE_CONFIGS,
  SCHEMA_SELECT_TYPE,
  SCHEMA_SELECT_TYPES,
  SCHEMA_XXX_OF_TYPES
} from '@/consts/ApiConstants'
import { $i18nBundle, $i18nConcat, $i18nKey } from '@/messages'
import { $ref2Schema, calcComponentOptions, fromSchemaToModel, hasXxxOf } from '@/services/api/ApiDocPreviewService'
import { getSingleSelectOptions } from '@/utils'
import { loadInfoDetails } from '@/api/ApiProjectInfoDetailApi'
import { cloneDeep, isArray } from 'lodash-es'
import { ElText } from 'element-plus'
import { showMarkdownWindow, toEditComponent } from '@/utils/DynamicUtils'
import { processSchemaBeforeSave } from '@/services/api/ApiDocEditService'

const vModel = ref()
const currentInfoDetail = ref()
const showWindow = ref(false)
const toEditJsonSchema = (data, currentInfo) => {
  vModel.value = cloneDeep(data)
  currentInfoDetail.value = currentInfo
  showWindow.value = true
  initJsonSchema()
}
const dataTypeCache = reactive({})
const initJsonSchema = () => {
  console.log('================model', vModel.value, currentInfoDetail.value)
  let xxxOf = null
  if (vModel.value?.schema?.$ref || vModel.value.dataType === SCHEMA_SELECT_TYPE.REF) {
    vModel.value.dataType = SCHEMA_SELECT_TYPE.REF
    vModel.value.type = vModel.value?.schema?.$ref
  } else if ((xxxOf = hasXxxOf(vModel.value?.schema)) || vModel.value.dataType === SCHEMA_SELECT_TYPE.XXX_OF) {
    vModel.value.dataType = SCHEMA_SELECT_TYPE.XXX_OF
    vModel.value.type = xxxOf
    fromSchemaToModel(vModel, xxxOf)
  } else { // 处理basic基本类型转换
    vModel.value.dataType = SCHEMA_SELECT_TYPE.BASIC
    vModel.value.type = vModel.value?.schema?.type
    vModel.value.basicSchema = vModel.value.schema
    vModel.value.enumEnabled = !!vModel.value.schema?.enum?.length
    vModel.value.constEnabled = !!vModel.value.schema?.const
    if (vModel.value.schema?.example) {
      vModel.value.schema.examples = [vModel.value.schema?.example]
    }
    if (vModel.value.type === 'array') {
      fromSchemaToModel(vModel, 'items', vModel.value.type)
    }
    if (vModel.value.type === 'object' && vModel.value.schema?.additionalProperties) {
      fromSchemaToModel(vModel, 'additionalProperties', vModel.value.type)
      vModel.value.additionalPropertiesEnabled = true
    }
  }
  dataTypeCache[vModel.value.dataType] = vModel.value.type
}

const processBeforeSave = () => processSchemaBeforeSave(vModel.value, additionalPropertiesEnabled.value)

const componentSchemas = ref([])
const loadComponentSchemas = () => {
  if (!componentSchemas.value.length && currentInfoDetail.value) {
    loadInfoDetails({
      projectId: currentInfoDetail.value.projectId,
      infoId: currentInfoDetail.value.infoId,
      bodyType: 'component'
    }).then(components => {
      componentSchemas.value = components || []
      emit('editComponentSchemas', components)
    })
  }
}
const getTypeOptions = (dataType) => {
  if (dataType === SCHEMA_SELECT_TYPE.BASIC) {
    return getSingleSelectOptions(...SCHEMA_BASE_TYPES)
  } else if (dataType === SCHEMA_SELECT_TYPE.XXX_OF) {
    loadComponentSchemas()
    return getSingleSelectOptions(...SCHEMA_XXX_OF_TYPES)
  } else if (dataType === SCHEMA_SELECT_TYPE.REF) {
    loadComponentSchemas()
    return calcComponentOptions(componentSchemas.value)
  }
}

const newTypeRefOption = {
  tooltip: $i18nKey('common.label.commonAdd', 'api.label.typeRef'),
  tooltipIcon: 'CirclePlusFilled',
  tooltipLinkAttrs: {
    type: 'primary'
  },
  tooltipFunc (event) {
    toEditComponent({
      projectId: currentInfoDetail.value.projectId,
      infoId: currentInfoDetail.value.infoId,
      schemaContent: '{"type":"object","properties":{}}',
      bodyType: 'component'
    }, {
      onSaveComponent: () => {
        componentSchemas.value = []
        loadComponentSchemas()
      }
    })
    event.preventDefault()
  }
}

const formOptions = computed(() => {
  const typeOptions = getTypeOptions(vModel.value.dataType)
  return [{
    labelKey: 'common.label.name',
    prop: 'name',
    required: true
  }, {
    labelKey: 'api.label.dataType',
    type: 'segmented',
    prop: 'dataType',
    required: true,
    attrs: {
      clearable: false,
      options: SCHEMA_SELECT_TYPES.map(item => ({
        value: item.value,
        label: $i18nBundle(item.labelKey)
      }))
    },
    change (dataType) {
      vModel.value.type = dataTypeCache[dataType]
    }
  }, {
    labelKey: SCHEMA_SELECT_TYPES.find(type => type.value === vModel.value.dataType)?.labelKey,
    prop: 'type',
    type: 'select-v2',
    required: true,
    attrs: {
      clearable: false,
      filterable: true,
      options: typeOptions
    },
    dynamicOption () {
      if (vModel.value.dataType === SCHEMA_SELECT_TYPE.REF) {
        return newTypeRefOption
      }
    },
    change (type) {
      if (vModel.value.dataType === SCHEMA_SELECT_TYPE.XXX_OF) {
        vModel.value[type] = vModel.value[type] || []
      }
      if (vModel.value.type === 'array') {
        fromSchemaToModel(vModel, 'items', vModel.value.type)
      }
      dataTypeCache[vModel.value.dataType] = type
    }
  }]
})

const basicConfig = computed(() => {
  if (vModel.value.dataType === SCHEMA_SELECT_TYPE.BASIC) {
    return SCHEMA_BASIC_TYPE_CONFIGS.find(config => config.value === vModel.value.type)
  }
  return undefined
})

const getPropConfig = (config, key) => {
  return config?.supportedPropConfigs?.find(propConfig => propConfig.name === key)
}

const showMoreOptionsBtn = computed(() => {
  return !!basicConfig.value?.supportedPropConfigs?.find(propConfig => propConfig.startMore)
})
const showMoreOptions = ref(false)
const additionalOptions = computed(() => {
  const basicEnabled = vModel.value.dataType === SCHEMA_SELECT_TYPE.BASIC
  const config = basicConfig.value
  console.log('=========================config', config)
  let isMoreIndex = 100
  const basicOpts = basicEnabled
    ? config?.supportedPropConfigs?.filter(propConfig => propConfig.type !== 'switch')
      .map((propConfig, index) => {
        const customOption = {}
        if (['enum', 'const'].includes(propConfig.name)) {
          customOption.enabled = !!vModel.value[`${propConfig.name}Enabled`]
        }
        if (propConfig.name === 'properties') {
          customOption.formatter = function (properties) {
            const propKeys = Object.keys(properties || {})
            return <ElText lineClamp={2} type="info">
              {propKeys.map((prop) => {
                const schema = properties[prop]
                let type = schema?.type || 'object'
                if (isArray(type)) {
                  type = `[${type.join(', ')}]`
                }
                let actType = schema?.format || schema?.$ref || schema?.items?.$ref || ''
                actType = $ref2Schema(actType)
                return <><ElText type="primary">{prop}:</ElText> {type}{actType ? `<${actType}>` : ''}, </>
              })}
            </ElText>
          }
        }
        if (propConfig.startMore) {
          isMoreIndex = index
        }
        return {
          type: propConfig.type,
          labelKey: propConfig.labelKey || `api.label.${propConfig.name}`,
          prop: `basicSchema.${propConfig.name}`,
          children: propConfig.options ? getSingleSelectOptions(...propConfig.options) : undefined,
          enabled: index >= isMoreIndex ? showMoreOptions.value : true,
          attrs: {
            allowCreate: propConfig.name === 'format',
            filterable: true
          },
          ...customOption
        }
      }) || []
    : []
  return [...basicOpts, {
    labelKey: 'common.label.description',
    prop: 'schema.description',
    attrs: {
      type: 'textarea'
    },
    tooltip: $i18nBundle('common.label.newWindowEdit'),
    tooltipIcon: 'EditPen',
    tooltipLinkAttrs: {
      type: 'primary'
    },
    tooltipFunc () {
      showMarkdownWindow({
        content: vModel.value.schema?.description,
        title: $i18nKey('common.label.commonEdit', 'common.label.description')
      }, {
        'onUpdate:modelValue': value => {
          if (vModel.value.schema) {
            vModel.value.schema.description = value
          }
        }
      })
    }
  }]
})

const basicOptions = computed(() => {
  const basicEnabled = vModel.value.dataType === SCHEMA_SELECT_TYPE.BASIC
  if (basicEnabled) {
    return [{
      labelKey: 'api.label.required',
      prop: 'required',
      type: 'switch',
      enabled: !!getPropConfig(basicConfig.value, 'required')
    }, {
      labelKey: 'api.label.nullable',
      prop: 'basicSchema.nullable',
      labelWidth: '80px',
      type: 'switch',
      enabled: !!getPropConfig(basicConfig.value, 'nullable')
    }, {
      labelKey: 'api.label.deprecated',
      prop: 'basicSchema.deprecated',
      labelWidth: '80px',
      type: 'switch',
      enabled: !!getPropConfig(basicConfig.value, 'deprecated')
    }, {
      labelKey: 'api.label.enum',
      prop: 'enumEnabled',
      labelWidth: '80px',
      type: 'switch',
      change (val) {
        if (val && vModel.value.constEnabled) {
          vModel.value.constEnabled = false
          vModel.value.schema.const = null
        }
      },
      enabled: !!getPropConfig(basicConfig.value, 'enum')
    }, {
      labelKey: 'api.label.const',
      prop: 'constEnabled',
      labelWidth: '80px',
      type: 'switch',
      change (val) {
        if (val && vModel.value.enumEnabled) {
          vModel.value.enumEnabled = false
          vModel.value.schema.enum = null
        }
      },
      enabled: !!getPropConfig(basicConfig.value, 'const')
    }, {
      labelKey: 'api.label.additionalProperties',
      prop: 'additionalPropertiesEnabled',
      labelWidth: '80px',
      type: 'switch',
      enabled: !!getPropConfig(basicConfig.value, 'additionalProperties'),
      change (value) {
        if (value) {
          fromSchemaToModel(vModel, 'additionalProperties', vModel.value.type)
        }
      }
    }]
  }
  return []
})

const emit = defineEmits(['saveJsonSchema', 'editComponentSchemas'])
const saveJsonSchema = ({ form }) => {
  form.validate(valid => {
    if (valid) {
      console.log('====================schemaData', vModel.value)
      processBeforeSave()
      emit('saveJsonSchema', vModel.value)
      showWindow.value = false
    }
  })
  return false
}

const additionalPropertiesEnabled = computed(() => vModel.value.dataType === SCHEMA_SELECT_TYPE.BASIC &&
    vModel.value.type === 'object' && vModel.value.additionalPropertiesEnabled)

const xxxOfOptions = computed(() => {
  const labelKey = additionalPropertiesEnabled.value
    ? $i18nBundle('api.label.additionalProperties')
    : $i18nConcat(vModel.value.type, $i18nBundle('api.label.type'))
  return [{
    labelKey,
    type: 'segmented',
    prop: 'dataType',
    attrs: {
      clearable: false,
      size: 'small',
      options: SCHEMA_SELECT_TYPES.filter(item => ['basic', 'ref'].includes(item.value)).map(item => ({
        value: item.value,
        label: $i18nBundle(item.labelKey)
      }))
    },
    dynamicOption (model, option, addInfo) {
      return {
        ...(model.dataType === SCHEMA_SELECT_TYPE.REF ? newTypeRefOption : {}),
        change (value) {
          console.log('==============changeDataType', value, model, addInfo)
          const key = `${vModel.value.dataType}-${vModel.value.type}-${model.dataType}-${addInfo.index}`
          model.type = dataTypeCache[key]
        }
      }
    }
  }, {
    labelWidth: '1px',
    showLabel: false,
    labelKey,
    prop: 'type',
    type: 'select-v2',
    required: true,
    dynamicOption (model, option, addInfo) {
      const typeOptions = getTypeOptions(model.dataType)
      return {
        change (value) {
          console.log('==============changeType', value, model, addInfo)
          const key = `${vModel.value.dataType}-${vModel.value.type}-${model.dataType}-${addInfo.index}`
          dataTypeCache[key] = value
        },
        attrs: {
          style: { width: '440px' },
          clearable: false,
          filterable: true,
          options: typeOptions
        }
      }
    }
  }]
})

const checkShowXxxOfForm = computed(() => {
  if (vModel.value.dataType === SCHEMA_SELECT_TYPE.XXX_OF) {
    return !!vModel.value.type
  }
  if (vModel.value.dataType === SCHEMA_SELECT_TYPE.BASIC) {
    return vModel.value.type === 'array' || (vModel.value.type === 'object' && vModel.value.additionalPropertiesEnabled)
  }
  return false
})

const addNewXxxOfItem = () => {
  vModel.value[vModel.value.type] = vModel.value[vModel.value.type] || []
  return vModel.value[vModel.value.type].push({ dataType: SCHEMA_SELECT_TYPE.REF, schema: {} })
}

defineExpose({
  toEditJsonSchema
})
</script>

<template>
  <common-window
    v-model="showWindow"
    width="1000px"
    :title="$i18nKey('common.label.commonEdit','api.label.apiModel')"
    :close-on-click-modal="false"
    destroy-on-close
    :ok-click="saveJsonSchema"
  >
    <el-container class="flex-column">
      <common-form
        class="form-edit-width-90"
        label-width="150px"
        :show-buttons="false"
        :options="formOptions"
        :model="vModel"
      >
        <template v-if="checkShowXxxOfForm">
          <div
            v-for="(xxxOf, index) in vModel[vModel.type]"
            :key="index"
            class="common-subform el-form--inline"
          >
            <common-form-control
              v-for="(option, optIdx) in xxxOfOptions"
              :key="`${index}-${optIdx}`"
              :model="xxxOf"
              :option="option"
              :add-info="{index}"
              :prop="`${vModel.type}.${index}.${option.prop}`"
            />
            <el-form-item label-width="1px">
              <el-button
                v-if="vModel.dataType===SCHEMA_SELECT_TYPE.XXX_OF"
                type="danger"
                size="small"
                @click.prevent="vModel[vModel.type].splice(index, 1)"
              >
                <common-icon icon="Delete" />
              </el-button>
            </el-form-item>
          </div>
          <el-form-item v-if="vModel.dataType===SCHEMA_SELECT_TYPE.XXX_OF">
            <el-button
              type="primary"
              size="small"
              @click="addNewXxxOfItem"
            >
              <common-icon
                icon="Plus"
                class="margin-right1"
              />
              {{ $t('common.label.add') }}
            </el-button>
          </el-form-item>
        </template>
        <div
          v-if="basicOptions.length"
          class="common-subform el-form--inline"
        >
          <common-form-control
            v-for="(option, optIdx) in basicOptions"
            :key="optIdx"
            :model="vModel"
            :option="option"
            :prop="option.prop"
          />
        </div>
        <common-form-control
          v-for="(option) in additionalOptions"
          :key="`${option.prop}`"
          :model="vModel"
          :option="option"
          :prop="option.prop"
        />
        <el-form-item v-if="showMoreOptionsBtn">
          <el-button
            :type="showMoreOptions?'info':'primary'"
            size="small"
            @click="showMoreOptions=!showMoreOptions"
          >
            <common-icon
              :icon="showMoreOptions?'ArrowUpBold':'ArrowDownBold'"
              class="margin-right1"
            />
            {{ $t(showMoreOptions?'api.label.hideMoreOptions':'api.label.showMoreOptions') }}
          </el-button>
        </el-form-item>
      </common-form>
      <!--      <pre>{{ vModel }}</pre>-->
    </el-container>
  </common-window>
</template>

<style scoped>

</style>
