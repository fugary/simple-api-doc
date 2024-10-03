<script setup>
import { computed, watch, ref, nextTick } from 'vue'
import { cloneDeep, isArray } from 'lodash-es'
import markdownit from 'markdown-it'
import { $i18nBundle } from '@/messages'
const md = markdownit({
  html: true,
  linkify: true
})

const props = defineProps({
  componentSchemas: {
    type: Array,
    default: () => ([])
  },
  specVersion: {
    type: String,
    default: 'V30'
  }
})

const schemaModel = defineModel({
  type: [Object, Array],
  default: () => ({})
})

const showTree = ref(false)
watch(schemaModel, () => {
  showTree.value = false
  console.log('==========================schemaModel', schemaModel.value)
  nextTick(() => {
    showTree.value = true
  })
}, { immediate: true })

const componentsMap = computed(() => {
  return props.componentSchemas.reduce((res, apiSchema) => {
    if (!apiSchema.schema && apiSchema.schemaContent) {
      apiSchema.schema = JSON.parse(apiSchema.schemaContent) // 从文本解析出来
    }
    res[apiSchema.schemaKey] = apiSchema
    return res
  }, {})
})

const checkLeaf = schema => {
  return !['array'].includes(schema?.type) && !schema?.$ref && !Object.keys(schema?.properties || {}).length
}

const $ref2Schema = $ref => {
  // #/components/schemas/xxx截取
  return $ref.substring($ref.lastIndexOf('/') + 1)
}

/**
 * 特殊处理schema的属性为数组
 * @param schema
 * @return {{schema: *, name: *}[]}
 */
const processProperties = schema => {
  console.log('====================================processProperties', schema)
  const properties = schema.items?.properties || schema.properties || {}
  const schemaParent = schema.items || schema
  return Object.keys(properties).map(key => {
    const value = properties[key]
    if (value.$ref) {
      value.name = $ref2Schema(value.$ref)
    }
    if (value?.type === 'array' && value?.items) {
      if (value?.items?.$ref) {
        value.items.name = $ref2Schema(value.items.$ref)
        value.name = value.items.name
      }
    }
    if (schemaParent.required?.length) {
      value.isRequired = schemaParent.required.includes(key)
    }
    return {
      name: key,
      schema: value,
      isLeaf: checkLeaf(value)
    }
  })
}

const processSchemas = (docSchema) => {
  const saveSchema = JSON.parse(docSchema.schemaContent)
  let resultArr = []
  if (isArray(saveSchema)) {
    resultArr = saveSchema.map(schema => processSchema(schema))
  } else {
    resultArr = [processSchema(saveSchema)]
  }
  return resultArr.flatMap(data => {
    if (data.schema.type === 'object' && data.schema.properties) { // 对象不保留名称，仅用属性
      return processProperties(data.schema)
    }
    return [data]
  })
}

const processSchema = apiSchema => {
  if (apiSchema) {
    const schema = apiSchema.schema
    const isArray = schema?.type === 'array'
    if (isArray && schema.items) {
      if (schema.items?.$ref) {
        schema.items = processSchemaRef(schema.items)
      }
      processSchemaAllOf(schema.items)
    } else {
      if (schema?.$ref) {
        apiSchema.schema = processSchemaRef(schema)
      }
      processSchemaAllOf(apiSchema.schema)
      apiSchema.isLeaf = checkLeaf(apiSchema.schema)
    }
  }
  return apiSchema
}

const processSchemaRef = schema => {
  if (schema.$ref) {
    const apiSchema = cloneDeep(componentsMap.value[schema.$ref]?.schema)
    if (!apiSchema) {
      console.log('==============================$ref-null', schema.$ref, apiSchema)
    }
    apiSchema.name = $ref2Schema(schema.$ref)
    schema = apiSchema
  }
  return schema
}

const processSchemaAllOf = schema => {
  if (schema?.allOf?.length && !schema.properties) {
    let properties = {}
    schema.allOf.forEach(child => {
      properties = { ...properties, ...child.properties }
    })
    schema.properties = properties
  }
  return schema
}

const loadTreeNode = (node, resolve) => {
  console.log('===================node', node, schemaModel.value)
  if (!node.parent) { // 第一级
    let firstArr = []
    if (isArray(schemaModel.value)) {
      firstArr = schemaModel.value.flatMap(docSchema => processSchemas(docSchema))
    } else {
      firstArr = processSchemas(schemaModel.value)
    }
    resolve(firstArr)
  } else {
    // 下级node
    resolve(processProperties(processSchema(node.data)?.schema))
  }
}

const treeProps = {
  isLeaf: 'isLeaf'
}

const getMarkdownStr = data => {
  let example = ''
  if (data.schema?.example) {
    example = `${$i18nBundle('common.label.example')}: <code>${data.schema?.example}</code>`
  }
  const str = `${example}<br/>
  ${data.schema?.description || ''}`
  return md.render(str)
}

</script>

<template>
  <el-container class="flex-column">
    <el-tree
      v-if="showTree"
      :props="treeProps"
      class="doc-schema-tree"
      node-key="name"
      lazy
      :load="loadTreeNode"
    >
      <template #empty>
        <el-empty :description="$t('common.msg.noData')" />
      </template>
      <template #default="{node}">
        <div class="custom-tree-node">
          <el-tag
            type="primary"
            size="small"
            class="margin-right2"
          >
            <strong>
              {{ node.data.name }}
              <span
                v-if="node.data.required || node.data.schema?.isRequired"
                class="doc-schema-required"
              >*</span>
            </strong>
          </el-tag>
          <el-text
            type="info"
            class="margin-right2"
          >
            <strong>{{ node.data.schema?.type }}&nbsp;</strong>
            <span v-if="node.data.schema?.format||node.data.schema?.name">
              &lt;{{ node.data.schema?.format || node.data.schema?.name }}&gt;
            </span>
          </el-text>
          <el-text
            v-if="node.data.schema?.description"
            type="info"
            size="small"
            class="padding-left2"
            style="white-space: normal;"
          >
            <!--eslint-disable-next-line vue/no-v-html-->
            <span v-html="getMarkdownStr(node.data)" />
          </el-text>
        </div>
      </template>
    </el-tree>
  </el-container>
</template>

<style scoped>

</style>
