<script setup lang="jsx">
import { shallowRef, computed } from 'vue'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import CommonIcon from '@/components/common-icon/index.vue'
import { formatDate } from '@/utils'
import { ElText } from 'element-plus'
import { $i18nBundle } from '@/messages'

const props = defineProps({
  originalDoc: {
    type: Object,
    default: undefined
  },
  modifiedDoc: {
    type: Object,
    default: undefined
  }
})

const showDiffViewer = defineModel({
  type: Boolean,
  default: false
})

const diffOptions = {
  automaticLayout: true,
  formatOnType: true,
  formatOnPaste: true,
  readOnly: true
}
const diffEditorRef = shallowRef()
const handleMount = diffEditor => (diffEditorRef.value = diffEditor)

const calcFormatter = ({ originalDoc = props.originalDoc, modifiedDoc = props.modifiedDoc, key, modifiedKey, value }) => {
  return () => {
    modifiedKey = modifiedKey || key
    const type = modifiedDoc[modifiedKey] !== originalDoc[key] ? 'warning' : ''
    return <ElText type={type}>
      {value}
    </ElText>
  }
}

const calcApiDocItems = (originalDoc, modifiedDoc) => {
  if (originalDoc && modifiedDoc) {
    return [{
      labelKey: 'common.label.version',
      formatter: () => originalDoc.docVersion
    }, {
      labelFormatter: calcFormatter({
        key: 'docVersion', value: $i18nBundle('common.label.version')
      }),
      formatter: calcFormatter({ key: 'docVersion', value: modifiedDoc.docVersion })
    }, {
      labelKey: 'common.label.modifyDate',
      formatter: () => {
        return formatDate(originalDoc.createDate)
      }
    }, {
      labelFormatter: calcFormatter({
        key: 'createDate', modifiedKey: 'modifyDate', value: $i18nBundle('common.label.modifyDate')
      }),
      formatter: calcFormatter({ key: 'createDate', modifiedKey: 'modifyDate', value: formatDate(modifiedDoc.modifyDate) })
    }, {
      labelKey: 'common.label.modifier',
      formatter: () => originalDoc.creator
    }, {
      labelFormatter: calcFormatter({
        key: 'creator', modifiedKey: 'modifier', value: $i18nBundle('common.label.modifier')
      }),
      formatter: calcFormatter({ key: 'creator', modifiedKey: 'modifier', value: modifiedDoc.modifier })
    }, {
      labelKey: 'api.label.docName',
      formatter: () => originalDoc.docName
    }, {
      labelFormatter: calcFormatter({
        key: 'docName', value: $i18nBundle('api.label.docName')
      }),
      formatter: calcFormatter({ key: 'docName', value: modifiedDoc.docName })
    }, {
      labelKey: 'common.label.status',
      formatter () {
        return <DelFlagTag v-model={originalDoc.status} />
      }
    }, {
      labelFormatter: calcFormatter({
        key: 'status', value: $i18nBundle('common.label.status')
      }),
      formatter () {
        return <DelFlagTag v-model={modifiedDoc.status} />
      }
    }, {
      labelKey: 'common.label.sortId',
      formatter: () => originalDoc.sortId
    }, {
      labelFormatter: calcFormatter({
        key: 'sortId', value: $i18nBundle('common.label.sortId')
      }),
      formatter: calcFormatter({ key: 'sortId', value: modifiedDoc.sortId })
    }, {
      labelKey: 'api.label.docContent',
      formatter: () => <CommonIcon icon="ArrowDownBold"/>
    }, {
      labelFormatter: calcFormatter({
        key: 'docContent', value: $i18nBundle('api.label.docContent')
      }),
      formatter: calcFormatter({ key: 'docContent', value: <CommonIcon icon="ArrowDownBold"/> })
    }]
  }
  return []
}

const descriptionsItems = computed(() => {
  return calcApiDocItems(props.originalDoc, props.modifiedDoc)
})

</script>

<template>
  <common-window
    v-model="showDiffViewer"
    :title="$t('api.label.versionDiff')"
    width="1000px"
    show-fullscreen
    :show-cancel="false"
    :ok-label="$t('common.label.close')"
  >
    <el-container class="flex-column">
      <common-descriptions
        class="form-edit-width-100 margin-bottom2"
        :column="2"
        border
        width="25%"
        :items="descriptionsItems"
      />
      <vue-monaco-diff-editor
        v-if="originalDoc && modifiedDoc"
        theme="vs-dark"
        :original="originalDoc.docContent"
        :modified="modifiedDoc.docContent"
        language="markdown"
        :options="diffOptions"
        style="height:500px;"
        @mount="handleMount"
      >
        <div v-loading="true" />
      </vue-monaco-diff-editor>
    </el-container>
  </common-window>
</template>

<style scoped>

</style>
