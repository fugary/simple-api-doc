<script setup>
import { computed } from 'vue'
import { cloneDeep, isArray } from 'lodash-es'

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

const componentsMap = computed(() => {
  return props.componentSchemas.reduce((res, apiSchema) => {
    if (!apiSchema.schema && apiSchema.schemaContent) {
      apiSchema.schema = JSON.parse(apiSchema.schemaContent) // 从文本解析出来
    }
    res[apiSchema.schemaKey] = apiSchema
    return res
  }, {})
})
/**
 * 特殊处理schema的属性为数组
 * @param schema
 * @return {{schema: *, name: *}[]}
 */
const processProperties = schema => {
  return Object.keys(schema.properties).map(key => {
    const value = schema.properties[key]
    return {
      name: key,
      schema: value,
      children: value.children
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
    if (data.schema.type === 'object' && data.schema.properties) { // json第一级对象不保留名称，仅用属性
      return processProperties(data.schema)
    }
    return [data]
  })
}

const processSchema = schema => {
  if (schema.schema?.$ref) {
    schema.schema = processSchemaRef(schema.schema)
  }
  if (schema.schema?.children) {
    schema.children = schema.schema.children
    delete schema.schema.children
  }
  return schema
}

const processSchemaRef = schema => {
  if (schema.$ref) {
    const apiSchema = cloneDeep(componentsMap.value[schema.$ref]?.schema)
    if (!apiSchema) {
      console.log('==============================$ref-null', schema.$ref, apiSchema)
    }
    schema = apiSchema
  }
  if (schema?.properties) {
    Object.keys(schema.properties).forEach(child => {
      schema.properties[child] = processSchemaRef(schema.properties[child])
    })
    schema.children = processProperties(schema)
  }
  return schema
}

const treeNodes = computed(() => {
  console.log('================================schemaModel', schemaModel.value)
  if (schemaModel.value) {
    if (isArray(schemaModel.value)) {
      return schemaModel.value.flatMap(docSchema => processSchemas(docSchema))
    } else {
      return processSchemas(schemaModel.value)
    }
  }
  return []
})

</script>

<template>
  <el-container class="flex-column">
    <el-tree
      class="doc-schema-tree"
      node-key="name"
      :data="treeNodes"
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
                v-if="node.data.required"
                class="doc-schema-required"
              >*</span>
            </strong>
          </el-tag>
          <el-text
            type="info"
            class="margin-right2"
          >
            <strong>{{ node.data.schema?.format || node.data.schema?.type }}</strong>
          </el-text>
          <el-text
            v-if="node.data.schema?.description"
            type="info"
            size="small"
            style="word-break: break-all;"
          >
            {{ node.data.schema.description }}
          </el-text>
        </div>
      </template>
    </el-tree>
  </el-container>
</template>

<style scoped>

</style>
