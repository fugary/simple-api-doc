<script setup lang="jsx">
import { defineFormOptions } from '@/components/utils'
import { computed, ref } from 'vue'
import { $copyText, getSingleSelectOptions, toFlatKeyValue } from '@/utils'
import { $i18nBundle, $i18nKey } from '@/messages'
import { ElMessage, ElButton } from 'element-plus'
import { calcSuggestionsFunc, concatValueSuggestions } from '@/services/api/ApiCommonService'
import { isFunction, cloneDeep } from 'lodash-es'
import { useRenderKey, useSortableParams } from '@/hooks/CommonHooks'
import { useTabFocus } from '@/hooks/useTabFocus'

const props = defineProps({
  formProp: {
    type: String,
    default: 'requestParams'
  },
  nameReadOnly: {
    type: Boolean,
    default: false
  },
  nameRequired: {
    type: Boolean,
    default: false
  },
  valueReadOnly: {
    type: Boolean,
    default: false
  },
  valueRequired: {
    type: Boolean,
    default: false
  },
  showEnableSwitch: {
    type: Boolean,
    default: true
  },
  showAddButton: {
    type: Boolean,
    default: true
  },
  showCopyButton: {
    type: Boolean,
    default: true
  },
  showPasteButton: {
    type: Boolean,
    default: true
  },
  showRemoveButton: {
    type: Boolean,
    default: true
  },
  nameKey: {
    type: String,
    default: 'name'
  },
  valueKey: {
    type: String,
    default: 'value'
  },
  nameSuggestions: {
    type: [Array, Function],
    default: () => []
  },
  valueSuggestions: {
    type: [Array, Function],
    default: () => []
  },
  fileFlag: {
    type: Boolean,
    default: false
  }
})

const params = defineModel('modelValue', {
  type: Array,
  default: () => []
})

const emit = defineEmits(['editObjectParam'])

params.value.forEach(param => (param.enabled = param.enabled ?? true))

const addRequestParam = () => {
  params.value.push({
    enabled: true
  })
}

const validParams = computed(() => {
  return params.value.filter(param => !!param.name && param.type !== 'file')
})

const copyParams = () => $copyText(JSON.stringify(validParams.value))

const calcPasteParams = value => {
  let calcParams = []
  if (value.startsWith('{')) { // json
    try {
      let objValue = JSON.parse(value)
      if (objValue != null) {
        objValue = toFlatKeyValue(objValue)
        calcParams = Object.keys(objValue).map(key => {
          return {
            enabled: true,
            [props.nameKey]: key,
            [props.valueKey]: objValue[key]
          }
        })
      }
    } catch (e) {
      ElMessage.error(e.message)
    }
  } else if (value.startsWith('[')) {
    calcParams = JSON.parse(value)
  } else {
    if (value.indexOf('?') > -1) {
      value = value.slice(value.indexOf('?') + 1)
    }
    calcParams = new URLSearchParams(value).entries().map(entry => {
      return {
        enabled: true,
        [props.nameKey]: entry[0],
        [props.valueKey]: entry[1]
      }
    })
  }
  return calcParams
}

const showTextModel = ref(false)
const inputTextModel = ref({
  text: ''
})
const inputTextOption = {
  tooltip: $i18nBundle('api.msg.pasteToProcess'),
  prop: 'text',
  labelWidth: '40px',
  attrs: {
    type: 'textarea'
  },
  change (value) {
    if (value) {
      const calcParams = calcPasteParams(value)
      params.value = [...calcParams]
      inputTextModel.value.text = ''
      showTextModel.value = false
    }
  }
}

const calcSuggestions = (key = 'name') => {
  const keySuggestions = props[`${key}Suggestions`]
  return calcSuggestionsFunc(keySuggestions)
}

const paramsOptions = computed(() => {
  const nameSuggestions = calcSuggestions('name')
  const valueSuggestions = calcSuggestions('value')
  return params.value.map(param => {
    const nvSpan = props.showEnableSwitch ? 8 : 10
    const paramValueSuggestions = concatValueSuggestions(param.valueSuggestions, valueSuggestions)
    return defineFormOptions([{
      labelWidth: '30px',
      prop: 'enabled',
      disabled: props.nameReadOnly,
      enabled: props.showEnableSwitch,
      type: 'switch',
      colSpan: 2
    }, {
      labelKey: 'common.label.name',
      prop: props.nameKey,
      required: props.nameReadOnly || props.nameRequired || param.nameRequired || param.valueRequired,
      disabled: props.nameReadOnly,
      colSpan: nvSpan,
      type: nameSuggestions ? 'autocomplete' : 'input',
      attrs: {
        fetchSuggestions: nameSuggestions,
        triggerOnFocus: false
      },
      dynamicOption: (item, ...args) => {
        if (isFunction(item.dynamicOption)) {
          return item.dynamicOption(item, ...args)
        }
      }
    }, {
      labelWidth: '1px',
      prop: 'type',
      type: 'select',
      value: 'text',
      disabled: props.valueReadOnly,
      children: getSingleSelectOptions('text', 'file'),
      attrs: {
        clearable: false,
        style: {
          paddingTop: '2px'
        }
      },
      enabled: props.fileFlag,
      colSpan: 3,
      change () {
        param[props.valueKey] = param.type === 'file' ? [] : ''
      }
    }, {
      labelKey: 'common.label.value',
      prop: props.valueKey,
      required: props.nameReadOnly || props.valueRequired || param.valueRequired,
      colSpan: nvSpan,
      disabled: props.valueReadOnly,
      enabled: param.type !== 'file',
      type: paramValueSuggestions ? 'autocomplete' : 'input',
      attrs: {
        fetchSuggestions: paramValueSuggestions,
        triggerOnFocus: false
      },
      dynamicOption: (item, ...args) => {
        if (isFunction(item.dynamicOption)) {
          return item.dynamicOption(item, ...args)
        }
      }
    }, {
      labelKey: 'common.label.files',
      type: 'upload',
      enabled: props.fileFlag && param.type === 'file',
      attrs: {
        fileList: param[props.valueKey],
        'onUpdate:fileList': (files) => {
          param[props.valueKey] = files
        },
        showFileList: true,
        autoUpload: false
      },
      slots: {
        trigger () {
          return <ElButton type="primary" size="small">{$i18nBundle('api.label.selectFile')}</ElButton>
        }
      },
      colSpan: 6
    }])
  })
})

const { sortableRef, hoverIndex, dragging } = useSortableParams(params, '.common-params-item')

const { renderKey } = useRenderKey()

useTabFocus(sortableRef)

</script>

<template>
  <el-container
    ref="sortableRef"
    class="flex-column common-params-edit"
  >
    <el-row
      v-for="(item, index) in params"
      :key="renderKey(item)"
      class="padding-bottom2 common-params-item"
      @mouseenter="hoverIndex=index"
      @mouseleave="hoverIndex=-1"
    >
      <template
        v-for="(option, idx) in paramsOptions[index]"
        :key="`${index}_${option.prop}`"
      >
        <el-col
          v-if="option.enabled!==false"
          :span="option.colSpan"
        >
          <common-form-control
            label-width="80px"
            :model="item"
            :option="option"
            :prop="`${formProp}.${index}.${option.prop}`"
          >
            <template
              v-if="idx===0"
              #beforeLabel
            >
              <common-icon
                :size="20"
                class="margin-top1 move-indicator"
                icon="DragIndicatorFilled"
                style="cursor: move;"
                :style="{ visibility: hoverIndex === index && !dragging ? 'visible' : 'hidden' }"
              />
            </template>
            <template
              v-if="item.isObject && option.prop === props.valueKey"
              #default
            >
              <el-tooltip
                :disabled="!item.value"
                placement="top"
                :show-after="300"
              >
                <template #content>
                  <el-scrollbar
                    max-height="250px"
                    class="object-param-preview-scroll"
                  >
                    <pre class="object-param-preview-text">{{ item.value }}</pre>
                  </el-scrollbar>
                </template>
                <div
                  style="display: flex; align-items: center; height: 32px;"
                >
                  <el-button
                    type="primary"
                    link
                    @click="emit('editObjectParam', item)"
                  >
                    <common-icon
                      icon="Edit"
                      class="margin-right1"
                    />
                    {{ $t('api.label.editObjectParam') }}
                  </el-button>
                </div>
              </el-tooltip>
            </template>
          </common-form-control>
        </el-col>
      </template>
      <el-col
        :span="3"
        class="padding-left2 padding-top1"
      >
        <el-button
          v-if="item.array"
          type="success"
          size="small"
          circle
          @click="params.splice(index + 1, 0, cloneDeep(item))"
        >
          <common-icon icon="Plus" />
        </el-button>
        <el-button
          v-if="showRemoveButton"
          type="danger"
          size="small"
          circle
          @click="params.splice(index, 1)"
        >
          <common-icon icon="Delete" />
        </el-button>
      </el-col>
      <el-col
        v-if="$slots.item"
        :span="24"
      >
        <slot
          name="item"
          :item="item"
          :index="index"
        />
      </el-col>
    </el-row>
    <el-row>
      <el-col>
        <el-button
          v-if="showAddButton"
          type="primary"
          size="small"
          @click="addRequestParam()"
        >
          <common-icon
            class="margin-right1"
            icon="Plus"
          />
          {{ $t('common.label.add') }}
        </el-button>
        <el-button
          v-if="showCopyButton&&validParams?.length"
          type="success"
          size="small"
          @click="copyParams()"
        >
          <common-icon
            class="margin-right1"
            icon="DocumentCopy"
          />
          {{ $i18nKey('common.label.commonCopy', 'api.label.params') }}
        </el-button>
        <el-button
          v-if="showPasteButton"
          :type="showTextModel?'success':'info'"
          size="small"
          @click="showTextModel=!showTextModel"
        >
          <common-icon
            class="margin-right1"
            icon="ContentPasteGoFilled"
          />
          {{ $t('common.label.paste') }}
        </el-button>
        <common-form-control
          v-if="showTextModel"
          class="padding-top2"
          :model="inputTextModel"
          :option="inputTextOption"
        />
      </el-col>
    </el-row>
  </el-container>
</template>

<style scoped>
.object-param-preview-scroll {
  max-width: 480px;
}
.object-param-preview-scroll :deep(.el-scrollbar__wrap) {
  scrollbar-width: thin;
  scrollbar-color: var(--el-border-color-darker) transparent;
}
.object-param-preview-scroll :deep(.el-scrollbar__wrap)::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
.object-param-preview-scroll :deep(.el-scrollbar__wrap)::-webkit-scrollbar-thumb {
  border-radius: 4px;
  background-color: var(--el-border-color-darker);
}
.object-param-preview-scroll :deep(.el-scrollbar__wrap)::-webkit-scrollbar-thumb:hover {
  background-color: var(--el-text-color-secondary);
}
.object-param-preview-scroll :deep(.el-scrollbar__wrap)::-webkit-scrollbar-track {
  background: transparent;
}
.object-param-preview-text {
  margin: 0;
  padding: 4px 8px 4px 2px;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
