<script setup>
import { computed } from 'vue'
import { $i18nBundle } from '@/messages'
import markdownit from 'markdown-it'
import { hasXxxOf } from '@/services/api/ApiDocPreviewService'
import { $copyText } from '@/utils'
import { isString } from 'lodash-es'

const props = defineProps({
  data: {
    type: Object,
    default: () => ({})
  }
})

const md = markdownit({
  html: true,
  linkify: true
})

const getMarkdownStr = data => {
  let str = ''
  if (data.schema?.description) {
    str = `${data.schema?.description}<br>`
  }
  if (data.schema?.example) {
    const exampleStr = isString(data.schema.example) ? data.schema.example : JSON.stringify(data.schema.example)
    str += `${$i18nBundle('common.label.example')}: <span class="el-text el-text--small el-text--primary">${exampleStr}</span>`
  }
  return md.render(str)
}

const treeNodeDescription = computed(() => getMarkdownStr(props.data))

const schemaXxxOf = computed(() => hasXxxOf(props.data?.schema))

</script>

<template>
  <div class="schema-tree-node">
    <el-tag
      v-if="data.name"
      type="primary"
      size="small"
      class="margin-right2"
      @click="$copyText(data.name)"
    >
      <strong>
        <el-text
          size="small"
          :type="data.schema?.deprecated?'warning':'primary'"
          :tag="data.schema?.deprecated?'del':'span'"
        >
          {{ data.name }}
        </el-text>
        <span
          v-if="data.required || data.schema?.isRequired"
          class="doc-schema-required"
        >*</span>
      </strong>
    </el-tag>
    <el-tag
      v-if="schemaXxxOf"
      type="success"
      size="small"
      class="margin-right2"
    >
      <strong>
        {{ schemaXxxOf }}
      </strong>
    </el-tag>
    <el-tag
      v-if="data.isAdditional"
      type="success"
      size="small"
      class="margin-right2"
    >
      <strong>
        {{ $t('api.label.additionalProperties') }}
      </strong>
    </el-tag>
    <el-text
      type="info"
      class="margin-right2"
    >
      <strong>{{ data.schema?.type||data.schema?.types?.[0] }}&nbsp;</strong>
      <span v-if="data.schema?.format||data.schema?.name">
        &lt;{{ data.schema?.format || data.schema?.name }}&gt;
      </span>
    </el-text>
    <el-text
      v-if="data.schema?.enum?.length"
      type="info"
      size="small"
      style="white-space: normal;"
    >
      {{ $t('api.label.enum') }}: <el-tag
        v-for="enumVal in data.schema?.enum"
        :key="enumVal"
        class="margin-left1"
        type="info"
        @click="$copyText(enumVal)"
      >
        {{ enumVal }}
      </el-tag>
    </el-text>
    <el-text
      v-if="treeNodeDescription"
      type="info"
      size="small"
      class="padding-left2"
      style="white-space: normal;"
    >
      <!--eslint-disable-next-line vue/no-v-html-->
      <span v-html="treeNodeDescription" />
    </el-text>
  </div>
</template>

<style scoped>

</style>
