<script setup lang="jsx">
import { computed } from 'vue'
import { calcComponentMap, processSchemaChildren, processSchemas } from '@/services/api/ApiDocPreviewService'
import { isArray } from 'lodash-es'
import { defineTableColumns } from '@/components/utils'
import { ElText } from 'element-plus'
import { $copyText } from '@/utils'
import { $i18nBundle } from '@/messages'
import markdownit from 'markdown-it'

const props = defineProps({
  componentSchemas: {
    type: Array,
    default: () => ([])
  },
  showMergeAllOf: {
    type: Boolean,
    default: true
  }
})

const schemaModel = defineModel({
  type: [Object, Array],
  default: () => ({})
})
const calcSchemaChildren = (docSchema) => {
  const resultArr = processSchemas(docSchema, componentsMap.value)
  return resultArr?.flatMap(schema => {
    if (schema.isLeaf) {
      return [schema]
    }
    return processSchemaChildren(schema.type === 'object' ? schema : schema.schema, props.showMergeAllOf)
  })
}
const componentsMap = computed(() => calcComponentMap(props.componentSchemas))
const schemas = computed(() => {
  let resultArr = []
  if (isArray(schemaModel.value)) {
    resultArr = schemaModel.value.flatMap(docSchema => calcSchemaChildren(docSchema))
  } else {
    resultArr = calcSchemaChildren(schemaModel.value)
  }
  return resultArr
})
const md = markdownit({
  html: true,
  linkify: true
})

const isParameter = computed(() => schemaModel.value?.bodyType === 'parameter')

const columns = computed(() => {
  return defineTableColumns([{
    labelKey: 'api.label.paramName',
    formatter (data) {
      return <ElText type={data.schema?.deprecated ? 'warning' : 'primary'}
                     tag={data.schema?.deprecated ? 'del' : 'span'}
                     underline={false} onClick={() => $copyText(data.name)}>
        {data.name}
        {data.required || data.schema?.isRequired ? <span class="doc-schema-required">*</span> : ''}
      </ElText>
    }
  }, {
    labelKey: 'api.label.paramType',
    formatter (data) {
      let type = data.schema?.name || data.schema?.type
      if (isArray(type)) {
        type = `[${type.join(', ')}]`
      }
      console.log('==============================dataType', type, data)
      return <ElText type="primary">
        {type}
      </ElText>
    }
  }, {
    labelKey: 'api.label.required',
    formatter (data) {
      if (data.required || data.schema?.isRequired) {
        return <ElText type="primary">
          {$i18nBundle('common.label.yes')}
        </ElText>
      }
    }
  }, {
    labelKey: 'api.label.paramIn',
    enabled: isParameter.value,
    formatter (data) {
      if (data.in) {
        return <ElText type="info" tag="i">
          {data.in}
        </ElText>
      }
    }
  }, {
    labelKey: 'common.label.description',
    formatter (data) {
      if (data.description || data.schema?.description) {
        return <span innerHTML={md.render(`${data.description || data.schema?.description}`)}/>
      }
    }
  }])
})
</script>

<template>
  <el-container class="flex-column">
    <h4 v-if="schemaModel.schemaName || schemaModel.contentType">
      {{ schemaModel.schemaName || schemaModel.contentType }}
    </h4>
    <el-container class="padding-10">
      <el-text
        v-if="schemaModel.description"
        type="info"
      >
        {{ schemaModel.description }}
      </el-text>
    </el-container>
    <common-table
      :columns="columns"
      :data="schemas"
    />
  </el-container>
</template>

<style scoped>

</style>
