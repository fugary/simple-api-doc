<script setup>
import UrlCopyLink from '@/views/components/api/UrlCopyLink.vue'
import ApiEnvPopover from '@/views/components/api/ApiEnvPopover.vue'
import CommonParamsEdit from '@/views/components/utils/CommonParamsEdit.vue'
import { computed, ref, watch } from 'vue'
import {
  checkParamsFilled,
  checkRequestBody,
  generateSampleCheckResults,
  isGetMethod
} from '@/services/api/ApiDocPreviewService'
import { useMonacoEditorOptions } from '@/vendors/monaco-editor'
import {
  AUTH_TYPE,
  calcContentType,
  NONE,
  FORM_DATA,
  FORM_URL_ENCODED,
  SPECIAL_LANGS,
  DEFAULT_HEADERS, REQUEST_SEND_MODES
} from '@/consts/ApiConstants'
import ApiRequestFormAuthorization from '@/views/components/api/form/ApiRequestFormAuthorization.vue'
import { $i18nBundle, $i18nConcat, $i18nKey } from '@/messages'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import { getSingleSelectOptions, $corePrompt, $coreSuccess, $coreWarning, $copyText } from '@/utils'
import { showCodeWindow } from '@/utils/DynamicUtils'
import { isString, isArray, cloneDeep } from 'lodash-es'
import {
  calcEnvSuggestions,
  calcHeaderSuggestions,
  generateFormSample,
  generateSchemaSample,
  removeSchemaRecursion
} from '@/services/api/ApiCommonService'
import { updateExamples } from '@/api/ApiProjectInfoDetailApi'

import ApiGenerateSample from '@/views/components/api/form/ApiGenerateSample.vue'
import ApiDataExample from '@/views/components/api/form/ApiDataExample.vue'
import NewWindowEditLink from '@/views/components/utils/NewWindowEditLink.vue'
import { buildCurlCommand, CURL_SHELL, extendCurlParams } from '@/services/api/CurlProcessService'
import { useShareDocTheme } from '@/services/api/ApiFolderService'

const props = defineProps({
  showAuthorization: {
    type: Boolean,
    default: false
  },
  newWindowEdit: {
    type: Boolean,
    default: true
  },
  responseTarget: {
    type: Object,
    default: undefined
  },
  schemaType: {
    type: String,
    default: 'json'
  },
  schemaBody: {
    type: [String, Object, Array],
    default: undefined
  },
  examples: {
    type: Array,
    default: () => []
  }
})

const paramTarget = defineModel('modelValue', {
  type: Object,
  default: () => ({})
})

const { contentRef, languageRef, editorRef, monacoEditorOptions, languageModel, normalLanguageSelectOption, formatDocument, checkEditorLang } = useMonacoEditorOptions({ readOnly: false })
const codeHeight = '300px'

const postSendContentOptions = [{
  value: FORM_DATA,
  label: 'form-data'
}, {
  value: FORM_URL_ENCODED,
  label: 'form-urlencoded'
}]

const paramsSendAsOption = computed(() => {
  return {
    labelKey: 'api.label.paramsSendAs',
    type: 'radio-group',
    prop: 'paramsSendAs',
    enabled: false,
    children: [{
      value: 'urlParams',
      label: 'query-params'
    }, ...postSendContentOptions]
  }
})

const customLanguageSelectOption = computed(() => {
  return {
    ...normalLanguageSelectOption.value,
    children: [...getSingleSelectOptions(NONE), ...normalLanguageSelectOption.value.children, ...postSendContentOptions]
  }
})
const showParamsSendAs = computed(() => {
  const { hasBody } = checkRequestBody(paramTarget.value.requestContentType || NONE,
    paramTarget.value, paramTarget.value.requestBody)
  return showRequestBody.value && !hasBody && paramTarget.value.requestParams?.length
})
const isSpecialLang = computed(() => SPECIAL_LANGS.includes(languageRef.value))
const isNone = computed(() => !languageRef.value || NONE === languageRef.value)
const isFormData = computed(() => FORM_DATA === languageRef.value)
const isFormUrlEncoded = computed(() => FORM_URL_ENCODED === languageRef.value)

const requestHeaderLength = computed(() => {
  return (paramTarget.value?.headerParams?.length || 0)
})

const showRequestBody = computed(() => {
  return !isGetMethod(paramTarget.value?.method)
})

watch(languageRef, lang => {
  paramTarget.value.requestFormat = lang
  paramTarget.value.requestContentType = calcContentType(lang, paramTarget.value.requestBody) || NONE
}, { immediate: true })

watch(contentRef, val => {
  paramTarget.value.requestBody = val
})

watch(() => [paramTarget.value.headerParams, paramTarget.value.requestParams], (allParams) => {
  allParams.forEach(eachParams => eachParams.forEach(param => {
    const newSuggestions = calcHeaderSuggestions(param.name)
    if (JSON.stringify(param.valueSuggestions) !== JSON.stringify(newSuggestions)) {
      param.valueSuggestions = newSuggestions
    }
  }))
}, { deep: true })

const currentTabName = ref('requestParamsTab')
const authContentModel = ref({})
const paramList = ['requestBody', 'pathParams', 'requestParams', 'headerParams']
const hasInheritAuth = ref(false)
const initParamTarget = () => {
  contentRef.value = paramTarget.value?.requestBody
  languageRef.value = paramTarget.value?.requestFormat || languageRef.value
  authContentModel.value = {
    authType: AUTH_TYPE.NONE,
    force: false
  }
  currentTabName.value = !isGetMethod(paramTarget.value.method) ? 'requestBodyTab' : 'requestParamsTab'
  for (const key of paramList) {
    if (paramTarget.value[key]?.length) {
      currentTabName.value = `${key}Tab`
      break
    }
  }
  hasInheritAuth.value = paramTarget.value.hasInheritAuth && !!paramTarget.value?.securityRequirements?.length
  if (paramTarget.value.authContent) {
    authContentModel.value = paramTarget.value.authContent
  } else {
    paramTarget.value.authContent = authContentModel.value
  }
  if (hasInheritAuth.value && !authContentModel.value.force) {
    authContentModel.value.authType = AUTH_TYPE.INHERIT
  }
  if (paramsSendAsOption.value.enabled && paramTarget.value && !paramTarget.value?.paramsSendAs) {
    paramTarget.value.paramsSendAs = !isGetMethod(paramTarget.value?.method) ? 'formUrlencoded' : 'urlParams'
  }
}
initParamTarget()
const authValid = ref(true)

const generateSample = async (schema) => {
  if ([FORM_URL_ENCODED, FORM_DATA].includes(schema.type)) {
    const bodyFormOptions = generateFormSample(schema)
    languageRef.value = schema.type
    paramTarget.value[languageRef.value] = bodyFormOptions
    console.log('================================lang', languageRef.value, bodyFormOptions, paramTarget.value)
  } else {
    contentRef.value = await generateSchemaSample(schema.schema, schema.type, {
      preferenceId: paramTarget.value.preferenceId,
      projectId: paramTarget.value.projectId,
      docId: paramTarget.value.docId
    })
    setTimeout(() => checkEditorLang())
  }
}
const selectExample = (example) => {
  contentRef.value = isString(example.value) ? example.value : JSON.stringify(example.value)
  setTimeout(() => checkEditorLang())
}

const envSuggestions = computed(() => calcEnvSuggestions(paramTarget.value?.groupConfig, paramTarget.value?.preferenceId))

const proxyModeOption = computed(() => {
  return {
    labelKey: 'api.label.sendType',
    tooltip: $i18nBundle('api.msg.sendTypeTooltip'),
    prop: 'sendType',
    type: 'select',
    children: REQUEST_SEND_MODES,
    style: 'margin-top:7px;',
    attrs: {
      clearable: false,
      style: {
        width: '150px'
      }
    }
  }
})

const viewSchemaBody = () => {
  if (isString(props.schemaBody)) {
    showCodeWindow(props.schemaBody, { theme: monacoTheme.value })
  } else {
    let calcSchemaBody = props.schemaBody
    if (isArray(props.schemaBody) && props.schemaBody.length === 1) {
      calcSchemaBody = props.schemaBody[0]
    }
    calcSchemaBody = removeSchemaRecursion(calcSchemaBody)
    const jsonStr = JSON.stringify(calcSchemaBody, (key, value) => {
      const isInternal = key.startsWith('__') || ['schema$ref', 'name', 'isLeaf'].includes(key)
      return isInternal ? undefined : value
    }, 2)
    showCodeWindow(jsonStr, { theme: monacoTheme.value })
  }
}

const supportedGenerates = computed(() => generateSampleCheckResults(props.schemaBody))

const isShare = computed(() => {
  return !!useShareConfigStore().sharePreferenceView[paramTarget.value?.preferenceId]?.isShare
})

const emit = defineEmits(['resetRequestForm', 'updateExamples'])

const getSchemaBodyId = () => {
  const body = isArray(props.schemaBody) && props.schemaBody.length === 1 ? props.schemaBody[0] : props.schemaBody
  return body?.__id
}

const doSaveExamples = (newExamples) => {
  const schemaId = getSchemaBodyId()
  if (!schemaId) {
    $coreWarning($i18nBundle('api.msg.saveExampleFailedNoId'))
    return
  }
  updateExamples({ id: schemaId, examples: JSON.stringify(newExamples) }).then(res => {
    if (res.success) {
      $coreSuccess($i18nBundle('common.msg.saveSuccess'))
      emit('updateExamples', newExamples)
      if (paramTarget.value) {
        paramTarget.value.requestExamples = [JSON.stringify(newExamples)]
      }
    }
  })
}

const saveAsExample = () => {
  $corePrompt($i18nBundle('common.msg.commonInput', [$i18nBundle('common.label.example')]),
    $i18nKey('common.label.commonSave', 'common.label.example'), {
      inputValue: 'Custom Example'
    }).then(({ value }) => {
    if (!value) return
    const newExamples = cloneDeep(props.examples || [])
    const existingIndex = newExamples.findIndex(e => e.summary === value)
    let exampleValue = contentRef.value
    try {
      exampleValue = JSON.parse(exampleValue)
    } catch {
      // Ignore invalid json
    }
    const newExample = { summary: value, value: exampleValue }
    if (existingIndex >= 0) {
      newExamples.splice(existingIndex, 1, newExample)
    } else {
      newExamples.push(newExample)
    }
    doSaveExamples(newExamples)
  }).catch(() => {})
}

const resetRequestForm = () => {
  emit('resetRequestForm')
  setTimeout(() => {
    initParamTarget()
  })
}

const curlContent = ref('')
const processCurlWindow = () => {
  showCodeWindow(curlContent, {
    title: $i18nConcat($i18nBundle('common.label.paste'), 'CURL'),
    showCancel: true,
    readOnly: false,
    language: 'shell',
    theme: monacoTheme.value,
    okLabel: $i18nBundle('common.label.confirm'),
    ok (str) {
      extendCurlParams(paramTarget, str)
      initParamTarget()
    }
  })
}

const CURL_COMMAND = {
  PASTE: 'paste',
  COPY_BASH: 'copyBash',
  COPY_CMD: 'copyCmd'
}

const handleCurlCommand = async (command) => {
  if (command === CURL_COMMAND.PASTE) {
    processCurlWindow()
  } else {
    const shell = command === CURL_COMMAND.COPY_CMD ? CURL_SHELL.CMD : CURL_SHELL.BASH
    $copyText(await buildCurlCommand(paramTarget.value, paramTarget.value.requestPath, shell))
  }
}

const { monacoTheme } = useShareDocTheme()

</script>

<template>
  <el-tabs
    v-model="currentTabName"
    type="border-card"
    class="form-edit-width-100 common-tabs"
    addable
  >
    <template
      #add-icon
    >
      <ApiEnvPopover
        :env-suggestions="envSuggestions"
        link-style="margin-top: -11px"
        link-class="margin-right2"
      />
      <el-dropdown
        class="margin-right2"
        @command="handleCurlCommand"
      >
        <el-link
          type="primary"
          style="margin-top: -11px"
        >
          CURL
        </el-link>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :command="CURL_COMMAND.PASTE">
              {{ $i18nConcat($i18nBundle('common.label.paste'), 'CURL') }}
            </el-dropdown-item>
            <el-dropdown-item :command="CURL_COMMAND.COPY_BASH">
              {{ $i18nConcat($i18nBundle('common.label.copy'), 'cURL (bash)') }}
            </el-dropdown-item>
            <el-dropdown-item :command="CURL_COMMAND.COPY_CMD">
              {{ $i18nConcat($i18nBundle('common.label.copy'), 'cURL (cmd)') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-link
        type="primary"
        style="margin-top: -11px"
        @click="resetRequestForm"
      >
        {{ $t('common.label.reset') }}
      </el-link>
      <common-form-control
        :option="proxyModeOption"
        :model="paramTarget"
      />
    </template>
    <el-tab-pane
      v-if="paramTarget.pathParams?.length"
      name="pathParamsTab"
    >
      <template #label>
        <el-badge
          :type="checkParamsFilled(paramTarget.pathParams) ? 'primary' : 'danger'"
          :value="paramTarget.pathParams?.length"
          :show-zero="false"
        >
          {{ $t('api.label.pathParams') }}
        </el-badge>
      </template>
      <common-params-edit
        v-model="paramTarget.pathParams"
        :show-remove-button="false"
        name-read-only
        form-prop="pathParams"
        :show-add-button="false"
        :show-paste-button="false"
        :value-suggestions="envSuggestions"
      />
    </el-tab-pane>
    <el-tab-pane name="requestParamsTab">
      <template #label>
        <el-badge
          :type="checkParamsFilled(paramTarget.requestParams) ? 'primary' : 'danger'"
          :value="paramTarget.requestParams?.length"
          :show-zero="false"
        >
          {{ $t('api.label.queryParams') }}
        </el-badge>
      </template>
      <common-form-control
        v-if="showParamsSendAs"
        :option="paramsSendAsOption"
        :model="paramTarget"
      />
      <common-params-edit
        v-model="paramTarget.requestParams"
        form-prop="requestParams"
        :file-flag="paramTarget.paramsSendAs==='formData'"
        :value-suggestions="envSuggestions"
      />
    </el-tab-pane>
    <el-tab-pane name="headerParamsTab">
      <template #label>
        <el-badge
          :type="checkParamsFilled(paramTarget.headerParams) ? 'primary' : 'danger'"
          :value="requestHeaderLength"
          :show-zero="false"
        >
          {{ $t('api.label.requestHeaders') }}
        </el-badge>
      </template>
      <common-params-edit
        v-model="paramTarget.headerParams"
        form-prop="headerParams"
        :name-suggestions="DEFAULT_HEADERS"
        :value-suggestions="envSuggestions"
      />
    </el-tab-pane>
    <el-tab-pane
      v-if="showRequestBody"
      name="requestBodyTab"
    >
      <template #label>
        <el-badge
          type="primary"
          :hidden="isNone"
          is-dot
        >
          {{ $t('api.label.requestBody') }}
        </el-badge>
      </template>
      <el-container class="flex-column">
        <common-form-control
          :model="languageModel"
          :option="customLanguageSelectOption"
        >
          <template #childAfter>
            <url-copy-link
              :content="contentRef"
              :tooltip="$i18nKey('common.label.commonCopy', 'api.label.requestBody')"
            />
            <new-window-edit-link
              v-if="newWindowEdit&&!isSpecialLang"
              v-model="contentRef"
              :theme="monacoTheme"
              class="margin-left3"
            />
            <el-link
              v-common-tooltip="$i18nKey('common.label.commonFormat', 'api.label.requestBody')"
              type="primary"
              underline="never"
              class="margin-left3"
              @click="formatDocument"
            >
              <common-icon
                :size="18"
                icon="FormatIndentIncreaseFilled"
              />
            </el-link>
            <el-link
              v-if="schemaBody"
              v-common-tooltip="$i18nKey('common.label.commonView', 'common.label.schema')"
              type="primary"
              underline="never"
              class="margin-left3"
              @click="viewSchemaBody()"
            >
              <common-icon
                :size="18"
                icon="ContentPasteSearchFilled"
              />
            </el-link>
            <api-data-example
              v-if="examples.length"
              :examples="examples"
              :read-only="true"
              @select-example="selectExample"
            />
            <template v-if="supportedGenerates?.length">
              <api-generate-sample
                v-if="supportedGenerates.length>1"
                :schemas="supportedGenerates"
                :title="$t('common.label.generateRequestData')"
                @generate-sample="generateSample"
              />
              <el-link
                v-if="supportedGenerates.length===1"
                v-common-tooltip="$t('common.label.generateRequestData')"
                type="primary"
                underline="never"
                class="margin-left3"
                @click="generateSample(supportedGenerates[0])"
              >
                <common-icon
                  :size="18"
                  :icon="`custom-icon-${supportedGenerates[0]?.type}`"
                />
              </el-link>
            </template>

            <el-link
              v-if="!isShare"
              v-common-tooltip="$t('api.label.saveAsExample')"
              type="primary"
              underline="never"
              class="margin-left3"
              @click="saveAsExample"
            >
              <common-icon
                :size="18"
                icon="SaveFilled"
              />
            </el-link>
          </template>
        </common-form-control>
        <template v-if="isFormData || isFormUrlEncoded">
          <common-params-edit
            v-model="paramTarget[languageRef]"
            :form-prop="`${languageRef}`"
            :file-flag="isFormData"
            :value-suggestions="!isFormData?envSuggestions:null"
          />
        </template>
        <vue-monaco-editor
          v-if="!isSpecialLang"
          v-model:value="contentRef"
          :language="languageRef"
          :height="codeHeight"
          :theme="monacoTheme"
          :options="monacoEditorOptions"
          class="common-resize-vertical"
          @mount="editorRef=$event"
        />
      </el-container>
    </el-tab-pane>
    <el-tab-pane
      v-if="showAuthorization"
      name="authorizationTab"
    >
      <template #label>
        <el-badge
          :type="authValid ? 'primary' : 'danger'"
          :hidden="[AUTH_TYPE.NONE, AUTH_TYPE.INHERIT].includes(authContentModel.authType)"
          is-dot
        >
          {{ $t('api.label.authorization') }}
        </el-badge>
      </template>
      <ApiRequestFormAuthorization
        v-model="authContentModel"
        v-model:auth-valid="authValid"
        :inherit-enabled="hasInheritAuth"
        :group-config="paramTarget.groupConfig"
        :preference-id="paramTarget.preferenceId"
      />
    </el-tab-pane>
  </el-tabs>
</template>

<style scoped>

</style>
