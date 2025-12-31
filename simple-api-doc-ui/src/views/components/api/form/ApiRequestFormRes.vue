<script setup>
import { computed, ref, watch } from 'vue'
import { useMonacoEditorOptions } from '@/vendors/monaco-editor'
import { $i18nKey, $i18nBundle } from '@/messages'
import UrlCopyLink from '@/views/components/api/UrlCopyLink.vue'
import { isString } from 'lodash-es'
import { $coreError } from '@/utils'
import { isJson, isXml } from '@/services/api/ApiCommonService'
import { showJsonDataWindow } from '@/utils/DynamicUtils'

const props = defineProps({
  responseTarget: {
    type: Object,
    default: undefined
  }
})

const paramTarget = defineModel('modelValue', {
  type: Object,
  default: () => ({})
})

const currentTabName = ref('responseData')

const {
  contentRef, languageRef, editorRef, monacoEditorOptions,
  languageModel, normalLanguageSelectOption, formatDocument
} = useMonacoEditorOptions()

const responseImg = ref()

watch(() => props.responseTarget, (responseTarget) => {
  currentTabName.value = 'responseData'
  responseImg.value = null
  const contentType = responseTarget?.responseHeaders?.find(header => header.name?.toLowerCase() === 'content-type' && header.value?.includes('image'))
  if (responseTarget?.data && contentType) {
    if (isString(responseTarget.data)) {
      $coreError($i18nBundle('api.msg.checkImageAccept'))
    } else {
      const base64Data = btoa(new Uint8Array(responseTarget.data).reduce((data, byte) => data + String.fromCharCode(byte), '')) // 将 ArrayBuffer 转换为 Base64
      responseImg.value = `data:${contentType};base64,${base64Data}`
    }
  } else {
    contentRef.value = responseTarget?.data
    setTimeout(() => formatDocument())
  }
}, { immediate: true })

const jsonResponseData = computed(() => isJson(props.responseTarget?.data) || isXml(props.responseTarget?.data))
const toShowJsonDataWindow = () => {
  paramTarget.value.tableConfig = paramTarget.value.tableConfig || {}
  return showJsonDataWindow(props.responseTarget?.data, {
    tableConfig: paramTarget.value.tableConfig,
    'onUpdate:tableConfig': (value) => {
      paramTarget.value.tableConfig = value
    }
  })
}

const requestInfo = computed(() => {
  return props.responseTarget?.requestInfo
})

const codeHeight = '300px'
</script>

<template>
  <el-container
    class="flex-column padding-top2"
  >
    <el-tabs
      v-model="currentTabName"
      type="border-card"
      class="form-edit-width-100 margin-top3 common-tabs"
      addable
    >
      <template
        #add-icon
      >
        <div
          v-if="responseTarget"
          style="display: flex; margin-top: -7px;"
        >
          <el-text
            v-common-tooltip="responseTarget?.error?.message"
            :type="requestInfo.status===200?'success':'danger'"
            class="padding-right3"
          >
            Status: {{ requestInfo.status }}
          </el-text>
          <el-text
            type="success"
            class="padding-right3"
          >
            Method: {{ requestInfo.method }}
          </el-text>
          <el-text
            type="success"
            class="padding-right3"
          >
            Duration: {{ requestInfo.duration }}ms
          </el-text>
        </div>
      </template>
      <el-tab-pane
        v-if="responseTarget"
        name="responseData"
      >
        <template #label>
          <el-badge
            type="primary"
            :hidden="!responseTarget?.data?.length"
            is-dot
          >
            {{ $t('api.label.responseBody') }}
          </el-badge>
        </template>
        <el-container class="flex-column">
          <template v-if="responseImg">
            <img
              :src="responseImg"
              alt="response image"
            >
          </template>
          <template v-else>
            <common-form-control
              :model="languageModel"
              :option="normalLanguageSelectOption"
              @change="languageRef=$event"
            >
              <template #childAfter>
                <url-copy-link
                  :content="contentRef"
                  :tooltip="$i18nKey('common.label.commonCopy', 'api.label.responseBody')"
                />
                <el-link
                  v-common-tooltip="$i18nKey('common.label.commonFormat', 'api.label.responseBody')"
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
                  v-common-tooltip="$t('api.msg.showRawData')"
                  type="primary"
                  underline="never"
                  class="margin-left3"
                  @click="contentRef=responseTarget?.data"
                >
                  <common-icon
                    :size="40"
                    icon="RawOnFilled"
                  />
                </el-link>
                <el-link
                  v-if="jsonResponseData"
                  v-common-tooltip="$t('api.label.viewAsTable')"
                  type="primary"
                  underline="never"
                  class="margin-left3"
                  @click="toShowJsonDataWindow()"
                >
                  <common-icon
                    :size="18"
                    icon="TableRowsFilled"
                  />
                </el-link>
              </template>
            </common-form-control>
            <vue-monaco-editor
              v-model:value="contentRef"
              :language="languageRef"
              :height="codeHeight"
              :options="monacoEditorOptions"
              class="common-resize-vertical"
              @mount="editorRef=$event"
            />
          </template>
        </el-container>
      </el-tab-pane>
      <el-tab-pane
        v-if="responseTarget"
        name="responseHeaders"
      >
        <template #label>
          <el-badge
            type="primary"
            :value="responseTarget.responseHeaders?.length"
            :show-zero="false"
          >
            {{ $t('api.label.responseHeaders') }}
          </el-badge>
        </template>
        <el-descriptions
          :column="1"
          class="form-edit-width-100"
          border
        >
          <el-descriptions-item
            v-for="info in responseTarget.responseHeaders"
            :key="info.name"
            :label="info.name"
            min-width="150px"
          >
            {{ info.value }}
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
      <el-tab-pane
        v-if="responseTarget?.requestHeaders?.length"
        name="requestHeaders"
      >
        <template #label>
          <el-badge
            type="primary"
            :value="responseTarget.requestHeaders?.length"
            :show-zero="false"
          >
            {{ $t('api.label.requestHeaders') }}
          </el-badge>
        </template>
        <el-descriptions
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
    </el-tabs>
  </el-container>
</template>

<style scoped>

</style>
