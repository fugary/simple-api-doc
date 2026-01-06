<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, useTemplateRef, watch } from 'vue'
import { useMonacoEditorOptions } from '@/vendors/monaco-editor'
import { $i18nKey, $i18nBundle } from '@/messages'
import UrlCopyLink from '@/views/components/api/UrlCopyLink.vue'
import { isString } from 'lodash-es'
import { $coreConfirm, $coreError } from '@/utils'
import { isJson, isXml } from '@/services/api/ApiCommonService'
import { showJsonDataWindow } from '@/utils/DynamicUtils'
import {
  downloadByLink,
  getDownloadConfirmConfig,
  isHtmlContentType,
  isMediaContentType,
  isStreamContentType
} from '@/services/api/ApiDocPreviewService'
import { calcContentLanguage } from '@/consts/ApiConstants'

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

const currentElRef = useTemplateRef('currentElRef')
const mediaConfig = reactive({
  responseImg: null,
  responseAudio: null,
  responseVideo: null,
  responseHtml: null,
  responseStream: null
})
const previewHtml = ref(false)
const clearMediaItems = (remove) => {
  for (const key in mediaConfig) {
    remove && currentElRef.value?.$el?.querySelector(`.${key}El`)?.remove()
    mediaConfig[key] = null
  }
}

onBeforeUnmount(() => clearMediaItems(true))
const mediaUrl = computed(() => {
  for (const key in mediaConfig) {
    if (mediaConfig[key] && key !== 'responseHtml') {
      return mediaConfig[key]
    }
  }
  return null
})
const downloadResponse = (url) => {
  const urlSegments = props.responseTarget?.requestInfo?.url?.split('/') || []
  downloadByLink(url, urlSegments[urlSegments.length - 1])
}
watch(() => props.responseTarget, async (responseTarget) => {
  currentTabName.value = 'responseData'
  const oriContentType = responseTarget?.responseHeaders?.find(header => header.name?.toLowerCase() === 'content-type')?.value
  const contentType = isMediaContentType(oriContentType) || isStreamContentType(oriContentType) || isHtmlContentType(oriContentType) ? oriContentType : undefined
  clearMediaItems()
  if (responseTarget?.data && contentType) {
    if (isString(responseTarget.data)) {
      if (isHtmlContentType(contentType)) {
        previewHtml.value = true
        mediaConfig.responseHtml = `data:text/html;charset=utf-8,${encodeURIComponent(responseTarget.data)}`
        contentRef.value = responseTarget.data
      } else {
        $coreError($i18nBundle('api.msg.checkImageAccept'))
      }
    } else {
      if (isMediaContentType(contentType)) {
        nextTick(() => {
          if (contentType?.includes('image')) {
            mediaConfig.responseImg = URL.createObjectURL(responseTarget.data)
          } else if (contentType?.includes('audio')) {
            mediaConfig.responseAudio = URL.createObjectURL(responseTarget.data)
          } else if (contentType?.includes('video')) {
            mediaConfig.responseVideo = URL.createObjectURL(responseTarget.data)
          }
        })
      } else {
        const streamUrl = mediaConfig.responseStream = URL.createObjectURL(responseTarget.data)
        $coreConfirm($i18nBundle('api.msg.previewStreamConfirm'), getDownloadConfirmConfig()).then(() => {
          downloadResponse(streamUrl)
        }, async () => {
          contentRef.value = await responseTarget?.data?.text?.()
        })
      }
    }
  } else {
    let content = responseTarget?.data
    if (responseTarget?.data instanceof Blob) {
      content = await responseTarget?.data.text()
    }
    contentRef.value = content
    const isRedirect = !!responseTarget?.responseHeaders?.find(header => header.name === 'mock-data-redirect')
    setTimeout(() => {
      if (isRedirect) {
        languageRef.value = 'text'
      } else {
        languageRef.value = calcContentLanguage(oriContentType) || languageRef.value
      }
      formatDocument()
    })
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
          <el-link
            v-if="mediaConfig.responseHtml"
            v-common-tooltip="previewHtml?$i18nKey('common.label.commonView', 'common.label.code'):$t('common.label.preview')"
            type="primary"
            underline="never"
            class="margin-right3"
            @click="previewHtml=!previewHtml"
          >
            <common-icon
              :size="previewHtml?20:18"
              :icon="previewHtml?'CodeOutlined':'View'"
            />
          </el-link>
          <el-link
            v-if="mediaUrl"
            v-common-tooltip="$t('api.label.downloadAsFile')"
            type="primary"
            underline="never"
            class="margin-right3"
            @click="downloadResponse(mediaUrl)"
          >
            <common-icon
              :size="18"
              icon="DownloadFilled"
            />
          </el-link>
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
          <img
            v-if="mediaConfig.responseImg"
            :src="mediaConfig.responseImg"
            alt="response image"
          >
          <audio
            v-else-if="mediaConfig.responseAudio"
            class="responseAudioEl"
            :src="mediaConfig.responseAudio"
            controls
          >
            Your browser does not support the audio element.
          </audio>
          <video
            v-else-if="mediaConfig.responseVideo"
            class="responseVideoEl"
            controls
            :src="mediaConfig.responseVideo"
          >
            Your browser does not support the video tag.
          </video>
          <iframe
            v-else-if="mediaConfig.responseHtml&&previewHtml"
            class="iframe-preview"
            :src="mediaConfig.responseHtml"
            height="500px"
          />
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
