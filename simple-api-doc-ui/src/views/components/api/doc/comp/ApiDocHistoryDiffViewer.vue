<script setup lang="jsx">
import { shallowRef, computed } from 'vue'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import UrlCopyLink from '@/views/components/api/UrlCopyLink.vue'
import CommonIcon from '@/components/common-icon/index.vue'
import { formatDate } from '@/utils'
import { ElText, ElTag } from 'element-plus'
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
    const newTag = !originalDoc.id ? <ElTag type="warning">{ $i18nBundle('common.label.new') }</ElTag> : ''
    const currentFlag = modifiedDoc.isCurrent ? <ElTag type="success" round={true}>{$i18nBundle('api.label.current')}</ElTag> : ''
    return [{
      labelKey: 'common.label.version',
      formatter: () => originalDoc.version
    }, {
      labelFormatter: calcFormatter({
        key: 'docVersion', value: $i18nBundle('common.label.version')
      }),
      formatter: calcFormatter({
        key: 'docVersion',
        value: <>
          <span class="margin-right2">{modifiedDoc.version}</span>
          {newTag}
          {currentFlag}
        </>
      })
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
      formatter: () => {
        return <>
          {originalDoc.docName}
          <UrlCopyLink class="margin-left1" showLink={!!originalDoc.docName} content={originalDoc.docName} />
        </>
      }
    }, {
      labelFormatter: calcFormatter({
        key: 'docName', value: $i18nBundle('api.label.docName')
      }),
      formatter: calcFormatter({
        key: 'docName',
        value: <>
          {modifiedDoc.docName}
          <UrlCopyLink class="margin-left1" showLink={!!modifiedDoc.docName} content={modifiedDoc.docName} />
        </>
      })
    }, {
      labelKey: 'common.label.status',
      formatter () {
        let lockStatus = <></>
        if (originalDoc.locked) {
          lockStatus = <CommonIcon icon="LockFilled" size={18} class="margin-left1"
                                   style="vertical-align: middle;"
                                   v-common-tooltip={$i18nBundle('api.msg.apiDocLocked')}/>
        }
        return <>
          <DelFlagTag v-model={originalDoc.status}/>
          {lockStatus}
        </>
      }
    }, {
      labelFormatter: calcFormatter({
        key: 'status', value: $i18nBundle('common.label.status')
      }),
      formatter () {
        let lockStatus = <></>
        if (modifiedDoc.locked) {
          const type = originalDoc.locked !== modifiedDoc.locked ? 'warning' : ''
          lockStatus = <ElText type={type}>
            <CommonIcon icon="LockFilled" size={18} class="margin-left1"
                        style="vertical-align: middle;"
                        v-common-tooltip={$i18nBundle('api.msg.apiDocLocked')}/>
          </ElText>
        }
        return <>
          <DelFlagTag v-model={modifiedDoc.status}/>
          {lockStatus}
        </>
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
      formatter: () => {
        return <>
          <CommonIcon icon="ArrowDownBold"/>
          <UrlCopyLink showLink={!!originalDoc.docContent} class="margin-left1" content={originalDoc.docContent} />
        </>
      }
    }, {
      labelFormatter: calcFormatter({
        key: 'docContent', value: $i18nBundle('api.label.docContent')
      }),
      formatter: calcFormatter({
        key: 'docContent',
        value: <>
          <CommonIcon icon="ArrowDownBold"/>
          <UrlCopyLink showLink={!!modifiedDoc.docContent} class="margin-left1" content={modifiedDoc.docContent} />
        </>
      })
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
