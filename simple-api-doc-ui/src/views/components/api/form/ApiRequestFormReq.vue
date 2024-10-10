<script setup>
import UrlCopyLink from '@/views/components/api/UrlCopyLink.vue'
import CommonParamsEdit from '@/views/components/utils/CommonParamsEdit.vue'
import { computed, ref, watch } from 'vue'
import { checkParamsFilled } from '@/services/api/ApiDocPreviewService'
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
import { $i18nBundle, $i18nKey } from '@/messages'
import { getSingleSelectOptions } from '@/utils'
import { showCodeWindow } from '@/utils/DynamicUtils'
import { isString } from 'lodash-es'
import { calcEnvSuggestions, generateSchemaSample } from '@/services/api/ApiCommonService'
import ApiGenerateSample from '@/views/components/api/form/ApiGenerateSample.vue'
import ApiDataExample from '@/views/components/api/form/ApiDataExample.vue'
import NewWindowEditLink from '@/views/components/utils/NewWindowEditLink.vue'

const props = defineProps({
  showAuthorization: {
    type: Boolean,
    default: false
  },
  newWindowEdit: {
    type: Boolean,
    default: false
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
    type: [String, Object],
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
contentRef.value = paramTarget.value?.requestBody
languageRef.value = paramTarget.value?.requestFormat || languageRef.value

const customLanguageSelectOption = computed(() => {
  return {
    ...normalLanguageSelectOption.value,
    children: [...getSingleSelectOptions(NONE), ...normalLanguageSelectOption.value.children, {
      value: FORM_DATA,
      label: 'form-data'
    }, {
      value: FORM_URL_ENCODED,
      label: 'form-urlencoded'
    }]
  }
})

const isSpecialLang = computed(() => SPECIAL_LANGS.includes(languageRef.value))
const isNone = computed(() => NONE === languageRef.value)
const isFormData = computed(() => FORM_DATA === languageRef.value)
const isFormUrlEncoded = computed(() => FORM_URL_ENCODED === languageRef.value)

const requestHeaderLength = computed(() => {
  return (paramTarget.value?.headerParams?.length || 0) + (props.responseTarget?.requestHeaders?.length || 0)
})

const showRequestBody = computed(() => {
  return paramTarget.value?.method !== 'GET'
})

watch(languageRef, lang => {
  paramTarget.value.requestFormat = lang
  paramTarget.value.requestContentType = calcContentType(lang, paramTarget.value.requestBody) || NONE
}, { immediate: true })

watch(contentRef, val => {
  paramTarget.value.requestBody = val
})

const currentTabName = ref('requestParamsTab')
const authContentModel = ref({
  authType: AUTH_TYPE.NONE
})
const paramList = ['requestBody', 'pathParams', 'requestParams', 'headerParams']
if (paramTarget.value) {
  currentTabName.value = paramTarget.value.method !== 'GET' ? 'requestBodyTab' : 'requestParamsTab'
  for (const key of paramList) {
    if (paramTarget.value[key]?.length) {
      currentTabName.value = `${key}Tab`
      break
    }
  }
  if (paramTarget.value.authContent) {
    authContentModel.value = paramTarget.value.authContent
  } else {
    paramTarget.value.authContent = authContentModel.value
  }
}
const authValid = ref(true)

const generateSample = async (type) => {
  contentRef.value = await generateSchemaSample(props.schemaBody, type)
  setTimeout(() => checkEditorLang())
}
const selectExample = (example) => {
  contentRef.value = isString(example.value) ? example.value : JSON.stringify(example.value)
  setTimeout(() => checkEditorLang())
}

const envSuggestions = computed(() => calcEnvSuggestions(paramTarget.value?.groupConfig))

const proxyModeOption = computed(() => {
  return {
    labelKey: 'api.label.sendType',
    tooltip: $i18nBundle('api.msg.sendTypeTooltip'),
    prop: 'sendType',
    type: 'select',
    children: REQUEST_SEND_MODES,
    style: 'margin-top:15px;',
    attrs: {
      clearable: false,
      style: {
        width: '150px'
      }
    }
  }
})

const viewSchemaBody = () => {
  const schemaBody = isString(props.schemaBody) ? props.schemaBody : JSON.stringify(props.schemaBody)
  showCodeWindow(schemaBody)
}

const supportXml = computed(() => {
  const schemaBody = isString(props.schemaBody) ? JSON.parse(props.schemaBody) : props.schemaBody
  return !!schemaBody?.xml
})

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
      <common-params-edit
        v-model="paramTarget.requestParams"
        form-prop="requestParams"
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
      <el-descriptions
        v-if="responseTarget"
        :column="1"
        class="form-edit-width-100 margin-top3"
        border
      >
        <el-descriptions-item
          v-for="info in responseTarget.requestHeaders"
          :key="info.name"
          :label="info.name"
          min-width="150px"
        >
          {{ info.value }}
        </el-descriptions-item>
      </el-descriptions>
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
              class="margin-left3"
            />
            <el-link
              v-common-tooltip="$i18nKey('common.label.commonFormat', 'api.label.requestBody')"
              type="primary"
              :underline="false"
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
              :underline="false"
              class="margin-left3"
              @click="viewSchemaBody()"
            >
              <common-icon
                :size="18"
                icon="ContentPasteSearchFilled"
              />
            </el-link>
            <api-generate-sample
              v-if="schemaBody&&supportXml"
              :title="$t('common.label.generateRequestData')"
              @generate-sample="generateSample"
            />
            <el-link
              v-if="schemaBody&&!supportXml"
              v-common-tooltip="$t('common.label.generateRequestData')"
              type="primary"
              :underline="false"
              class="margin-left3"
              @click="generateSample('json')"
            >
              <common-icon
                :size="18"
                icon="custom-icon-json"
              />
            </el-link>
            <api-data-example
              v-if="examples.length"
              :examples="examples"
              @select-example="selectExample"
            />
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
          :hidden="authContentModel.authType === AUTH_TYPE.NONE"
          is-dot
        >
          {{ $t('api.label.authorization') }}
        </el-badge>
      </template>
      <ApiRequestFormAuthorization
        v-model="authContentModel"
        v-model:auth-valid="authValid"
        :group-config="paramTarget.groupConfig"
      />
    </el-tab-pane>
  </el-tabs>
</template>

<style scoped>

</style>
