<script setup>
import { computed, watch, ref, nextTick } from 'vue'
import { isArray } from 'lodash-es'
import markdownit from 'markdown-it'
import { $i18nBundle } from '@/messages'
import { processSchema, processSchemas, processProperties, calcComponentMap } from '@/services/api/ApiDocPreviewService'
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
  nextTick(() => {
    showTree.value = true
  })
}, { immediate: true })

const componentsMap = computed(() => calcComponentMap(props.componentSchemas))

const loadTreeNode = (node, resolve) => {
  if (!node.parent) { // 第一级
    let firstArr = []
    if (isArray(schemaModel.value)) {
      firstArr = schemaModel.value.flatMap(docSchema => processSchemas(docSchema, componentsMap.value))
    } else {
      firstArr = processSchemas(schemaModel.value, componentsMap.value)
    }
    resolve(firstArr.map(data => {
      data.id = '_root'
      return data
    }))
  } else {
    // 下级node
    resolve(processProperties(processSchema(node.data, componentsMap.value)?.schema))
  }
}

const treeProps = {
  isLeaf: 'isLeaf'
}

const getMarkdownStr = data => {
  let example = ''
  if (data.schema?.example) {
    example = `${$i18nBundle('common.label.example')}: <code>${data.schema?.example}</code>\n`
  }
  const str = `${example}
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
      :default-expanded-keys="['_root']"
      node-key="id"
      lazy
      :load="loadTreeNode"
    >
      <template #empty>
        <el-empty :description="$t('common.msg.noData')" />
      </template>
      <template #default="{data}">
        <div class="custom-tree-node">
          <el-tag
            v-if="data.name"
            type="primary"
            size="small"
            class="margin-right2"
          >
            <strong>
              {{ data.name }}
              <span
                v-if="data.required || data.schema?.isRequired"
                class="doc-schema-required"
              >*</span>
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
            >
              {{ enumVal }}
            </el-tag>
          </el-text>
          <el-text
            v-if="data.schema?.description||data.schema?.example"
            type="info"
            size="small"
            class="padding-left2"
            style="white-space: normal;"
          >
            <!--eslint-disable-next-line vue/no-v-html-->
            <span v-html="getMarkdownStr(data)" />
          </el-text>
        </div>
      </template>
    </el-tree>
  </el-container>
</template>

<style scoped>

</style>
