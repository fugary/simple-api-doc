<script setup lang="jsx">
import { computed } from 'vue'
import markdownit from 'markdown-it'
import { hasXxxOf } from '@/services/api/ApiDocPreviewService'
import { $copyText } from '@/utils'
import { isString, isNil, isArray, isObject } from 'lodash-es'

const props = defineProps({
  data: {
    type: Object,
    default: () => ({})
  },
  showMergeAllOf: {
    type: Boolean,
    default: true
  }
})

const md = markdownit({
  html: true,
  linkify: true
})

const getMarkdownStr = data => {
  let str = '<br>'
  if (data.description || data.schema?.description) {
    str = `${data.description || data.schema?.description}<br>`
  }
  return md.render(str)
}

const treeNodeDescription = computed(() => getMarkdownStr(props.data))

const schemaXxxOf = computed(() => hasXxxOf(props.data?.schema))
const schemaXxxOfLength = computed(() => {
  const schema = props.data?.schema
  if (schemaXxxOf.value && schema) {
    return schema[schemaXxxOf.value]?.length || schema.items?.[schemaXxxOf.value]?.length
  }
  return 0
})

const exampleStr = computed(() => {
  let exampleStr = ''
  const example = props.data?.schema?.example ?? props.data?.schema?.default
  if (example) {
    exampleStr = isString(example) ? example : JSON.stringify(example)
  }
  return exampleStr
})

const dataExamples = computed(() => {
  const examples = props.data?.examples
  if (isObject(examples)) {
    return (Object.values(examples) || []).filter(item => !isNil(item.value))
  }
  return null
})

const otherParams = computed(() => {
  const schema = props.data?.schema
  if (schema) {
    return ['minLength', 'maxLength', 'pattern', 'minItems', 'maxItems', 'uniqueItems', 'maxProperties', 'minProperties', 'nullable', 'readOnly', 'writeOnly']
      .filter(name => !isNil(schema[name])).map(name => {
        return {
          name,
          value: schema[name]
        }
      })
  }
  return []
})

const typeStr = computed(() => {
  const type = props.data?.schema?.type
  if (isArray(type)) {
    return `[${type.join(', ')}]`
  }
  return type
})

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
          v-if="data.required"
          class="doc-schema-required"
        >*</span>
      </strong>
    </el-tag>
    <el-tag
      v-if="schemaXxxOf && (!showMergeAllOf || schemaXxxOfLength>1)"
      type="success"
      size="small"
      class="margin-right2"
    >
      <strong>
        {{ schemaXxxOf }} [{{ schemaXxxOfLength }}]
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
      <strong>{{ typeStr }}&nbsp;</strong>
      <span v-if="data.schema?.format||data.schema?.name||data.schema?.items?.name">
        &lt;{{ data.schema?.format || data.schema?.name || data.schema?.items?.name }}&gt;
      </span>
    </el-text>
    <el-text
      v-if="data.in"
      type="info"
      tag="i"
      class="margin-right2"
    >
      ({{ data.in }})
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
        type="primary"
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
    <template v-if="otherParams?.length">
      <el-text
        v-for="(otherParam, index) in otherParams"
        :key="index"
        size="small"
        type="info"
        tag="i"
        class="padding-left2"
      >
        {{ otherParam.name }}:
        <el-text
          type="primary"
          size="small"
        >
          {{ otherParam.value }}
        </el-text>
      </el-text>
    </template>
    <el-text
      v-if="exampleStr"
      size="small"
      type="info"
      class="padding-left2"
    >
      {{ $t('common.label.example') }}:
      <el-tag
        type="primary"
        size="small"
        @click="$copyText(exampleStr)"
      >
        {{ exampleStr }}
      </el-tag>
    </el-text>
    <el-text
      v-if="dataExamples?.length"
      size="small"
      type="info"
      class="padding-left2"
    >
      {{ $t('common.label.example') }}:
      <template
        v-for="(example, idx) in dataExamples"
        :key="idx"
      >
        <template v-if="example.value">
          <el-tag
            type="primary"
            size="small"
            class="margin-left2"
            @click="$copyText(example.value)"
          >
            {{ example.value }}
          </el-tag>
          <el-text
            type="info"
            size="small"
            class="margin-left1"
          >
            {{ example.description }}
          </el-text>
        </template>
      </template>
    </el-text>
  </div>
</template>

<style scoped>

</style>
