<script setup>
import { computed, ref, watch } from 'vue'
import { SCHEMA_BASE_TYPES, SCHEMA_SELECT_TYPE, SCHEMA_SELECT_TYPES, SCHEMA_XXX_OF_TYPES } from '@/consts/ApiConstants'
import { $i18nBundle, $i18nConcat, $i18nKey } from '@/messages'
import { calcComponentOptions, hasXxxOf } from '@/services/api/ApiDocPreviewService'
import { getSingleSelectOptions } from '@/utils'
import { loadInfoDetails } from '@/api/ApiProjectInfoDetailApi'

const vModel = ref()
const currentInfoDetail = ref()
const showWindow = ref(false)
const toEditJsonSchema = (data, currentInfo) => {
  vModel.value = data
  currentInfoDetail.value = currentInfo
  showWindow.value = true
  initJsonSchema()
}

const initJsonSchema = () => {
  console.log('================model', vModel.value, currentInfoDetail.value)
  vModel.value.dataType = SCHEMA_SELECT_TYPE.BASIC
  vModel.value.type = vModel.value?.schema?.type
  let xxxOf = null
  if (vModel.value?.schema?.$ref) {
    vModel.value.dataType = SCHEMA_SELECT_TYPE.REF
    vModel.value.type = vModel.value?.schema?.$ref
  } else if ((xxxOf = hasXxxOf(vModel.value?.schema))) {
    vModel.value.dataType = SCHEMA_SELECT_TYPE.XXX_OF
    vModel.value.type = xxxOf
    vModel.value[xxxOf] = vModel.value?.schema[xxxOf].map(xxxOfSchema => {
      return {
        dataType: xxxOfSchema?.$ref ? 'ref' : 'object',
        schema: xxxOfSchema,
        type: xxxOfSchema?.$ref || 'object'
      }
    })
  }
}

watch(() => vModel.value?.type, (type) => {
  if (type) {
    ['type', '$ref'].forEach(key => delete vModel.value.schema[key])
    if (vModel.value.dataType === SCHEMA_SELECT_TYPE.BASIC) {
      vModel.value.schema.type = type
    } else if (vModel.value.dataType === SCHEMA_SELECT_TYPE.REF) {
      vModel.value.schema.$ref = type
    } else if (vModel.value.dataType === SCHEMA_SELECT_TYPE.XXX_OF) {
      console.log('============type', type)
    }
  }
})

const componentSchemas = ref([])
const loadComponentSchemas = () => {
  if (!componentSchemas.value.length) {
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
    change () {
      vModel.value.type = ''
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
    change (type) {
      if (vModel.value.dataType === SCHEMA_SELECT_TYPE.XXX_OF) {
        vModel.value[type] = []
      }
    }
  }, {
    labelKey: 'common.label.description',
    prop: 'schema.description',
    attrs: {
      type: 'textarea'
    }
  }]
})

const emit = defineEmits(['saveJsonSchema', 'editComponentSchemas'])
const saveJsonSchema = ({ form }) => {
  form.validate(valid => {
    if (valid) {
      console.log('====================schemaData', vModel.value)
      emit('saveJsonSchema', vModel.value)
    }
  })
}

const xxxOfOptions = computed(() => {
  const typeOptions = [{
    value: 'object',
    label: 'Object'
  }, ...getTypeOptions(SCHEMA_SELECT_TYPE.REF)]
  return [{
    labelKey: $i18nConcat(vModel.value.type, '类型'),
    prop: 'type',
    type: 'select-v2',
    required: true,
    attrs: {
      clearable: false,
      filterable: true,
      options: typeOptions
    }
  }]
})

defineExpose({
  toEditJsonSchema
})
</script>

<template>
  <common-window
    v-model="showWindow"
    :title="$i18nKey('common.label.commonEdit','api.label.apiModel')"
    :close-on-click-modal="false"
    destroy-on-close
    :ok-click="saveJsonSchema"
  >
    <el-container class="flex-column">
      <common-form
        class="form-edit-width-90"
        :show-buttons="false"
        :options="formOptions"
        :model="vModel"
      >
        <template v-if="vModel.dataType==='xxxOf'">
          <div
            v-for="(xxlOf, index) in vModel[vModel.type]"
            :key="index"
            class="common-subform el-form--inline"
          >
            <common-form-control
              v-for="(option, optIdx) in xxxOfOptions"
              :key="`${index}-${optIdx}`"
              :model="xxlOf"
              :option="option"
              :prop="`${vModel.type}.${index}.${option.prop}`"
            />
            <el-form-item>
              <el-button
                type="danger"
                size="small"
                @click.prevent="vModel[vModel.type].splice(index, 1)"
              >
                <common-icon icon="Delete" />
              </el-button>
            </el-form-item>
          </div>
        </template>
      </common-form>
      <pre>{{ vModel }}</pre>
    </el-container>
  </common-window>
</template>

<style scoped>

</style>
