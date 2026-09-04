<script setup>
import { ref, computed } from 'vue'
import { defineMonacoOptions, $formatDocument } from '@/vendors/monaco-editor'
import { showCodeWindow } from '@/utils/DynamicUtils'
import { generateSchemaSample, removeSchemaRecursion } from '@/services/api/ApiCommonService'
import UrlCopyLink from '@/views/components/api/UrlCopyLink.vue'
import ApiDataExample from '@/views/components/api/form/ApiDataExample.vue'
import { isArray, isObject, isString } from 'lodash-es'
import { ElMessage } from 'element-plus'
import { $i18nBundle, $i18nKey } from '@/messages'

const props = defineProps({
  monacoTheme: {
    type: String,
    default: 'vs'
  },
  preferenceId: {
    type: [String, Number],
    default: null
  },
  projectId: {
    type: [String, Number],
    default: null
  },
  docId: {
    type: [String, Number],
    default: null
  }
})

const showWindow = ref(false)
const fullscreen = ref(false)
const currentParam = ref(null)
const editorContent = ref('')
const editorRef = ref()

const languageModel = ref({ language: 'json' })
const langOption = computed(() => ({
  labelKey: 'api.label.dataFormat',
  type: 'radio-group',
  prop: 'language',
  children: [{ label: 'JSON', value: 'json' }],
  attrs: {
    clearable: false
  }
}))

const monacoEditorOptions = defineMonacoOptions({
  readOnly: false,
  language: 'json',
  autoCheckLang: false
})

const onEditorMount = (editor) => {
  editorRef.value = editor
}

const formatDoc = () => {
  if (editorRef.value) {
    $formatDocument(editorRef.value, false)
  } else if (editorContent.value && isString(editorContent.value)) {
    try {
      editorContent.value = JSON.stringify(JSON.parse(editorContent.value), null, 2)
    } catch {
      // keep original if invalid
    }
  }
}

const viewSchema = () => {
  const schema = currentParam.value?.schema
  if (!schema) return
  const calcSchema = removeSchemaRecursion(schema)
  const jsonStr = JSON.stringify(calcSchema, (key, value) => {
    const isInternal = key.startsWith('__') || ['schema$ref', 'name', 'isLeaf'].includes(key)
    return isInternal ? undefined : value
  }, 2)
  showCodeWindow(jsonStr, { theme: props.monacoTheme })
}

const genSample = async () => {
  const schema = currentParam.value?.schema
  if (!schema) return
  const sampleData = await generateSchemaSample(schema, 'json', {
    preferenceId: props.preferenceId,
    projectId: props.projectId,
    docId: props.docId
  })
  if (sampleData) {
    editorContent.value = sampleData
    setTimeout(() => formatDoc())
  }
}

const paramExamples = computed(() => {
  const ex = currentParam.value?.examples || currentParam.value?.schema?.examples
  if (isArray(ex)) {
    return ex.map(e => isObject(e) ? e : { value: e, summary: String(e) })
  }
  if (isObject(ex)) {
    return Object.entries(ex).map(([k, v]) => {
      if (isObject(v)) {
        return { summary: v.summary || k, description: v.description, value: v.value ?? v }
      }
      return { summary: k, value: v }
    })
  }
  return []
})

const selectExample = (example) => {
  editorContent.value = isString(example.value) ? example.value : JSON.stringify(example.value, null, 2)
  setTimeout(() => formatDoc())
}

const open = (paramItem) => {
  currentParam.value = paramItem
  editorContent.value = paramItem?.value || ''
  showWindow.value = true
  setTimeout(() => formatDoc(), 100)
}

const handleConfirm = () => {
  if (editorContent.value && isString(editorContent.value) && editorContent.value.trim()) {
    try {
      JSON.parse(editorContent.value)
    } catch {
      ElMessage.warning($i18nBundle('common.msg.jsonError'))
    }
  }
  if (currentParam.value) {
    currentParam.value.value = editorContent.value
  }
  showWindow.value = false
}

const editorHeight = computed(() => {
  return fullscreen.value ? 'calc(100dvh - 280px)' : '350px'
})

defineExpose({
  open
})
</script>

<template>
  <common-window
    v-model="showWindow"
    v-model:fullscreen="fullscreen"
    :title="$t('api.label.editObjectParam')"
    width="850px"
    destroy-on-close
    append-to-body
    show-fullscreen
    :show-ok="true"
    :ok-label="$t('common.label.confirm')"
    :ok-click="handleConfirm"
    :show-cancel="true"
    :cancel-label="$t('common.label.cancel')"
  >
    <template #header>
      <div class="header-title">
        <span>{{ $t('api.label.editObjectParam') }}</span>
        <el-tag
          v-if="currentParam?.name"
          size="small"
          type="primary"
          effect="light"
        >
          {{ currentParam.name }}
        </el-tag>
      </div>
    </template>

    <el-container class="flex-column">
      <common-form-control
        :model="languageModel"
        :option="langOption"
      >
        <template #childAfter>
          <url-copy-link
            :content="editorContent"
            :tooltip="$i18nKey('common.label.commonCopy', 'api.label.objectParam')"
          />
          <el-link
            v-common-tooltip="$i18nKey('common.label.commonFormat', 'api.label.objectParam')"
            type="primary"
            underline="never"
            class="margin-left3"
            @click="formatDoc"
          >
            <common-icon
              :size="18"
              icon="FormatIndentIncreaseFilled"
            />
          </el-link>
          <el-link
            v-if="currentParam?.schema && Object.keys(currentParam.schema).length"
            v-common-tooltip="$i18nKey('common.label.commonView', 'common.label.schema')"
            type="primary"
            underline="never"
            class="margin-left3"
            @click="viewSchema"
          >
            <common-icon
              :size="18"
              icon="ContentPasteSearchFilled"
            />
          </el-link>
          <api-data-example
            v-if="paramExamples.length"
            :examples="paramExamples"
            :read-only="true"
            @select-example="selectExample"
          />
          <el-link
            v-if="currentParam?.schema && Object.keys(currentParam.schema).length"
            v-common-tooltip="$t('common.label.generateRequestData')"
            type="primary"
            underline="never"
            class="margin-left3"
            @click="genSample"
          >
            <common-icon
              :size="18"
              icon="custom-icon-json"
            />
          </el-link>
        </template>
      </common-form-control>

      <vue-monaco-editor
        v-model:value="editorContent"
        :options="monacoEditorOptions"
        :height="editorHeight"
        :theme="monacoTheme"
        language="json"
        @mount="onEditorMount"
      />
    </el-container>
  </common-window>
</template>

<style scoped>
.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
