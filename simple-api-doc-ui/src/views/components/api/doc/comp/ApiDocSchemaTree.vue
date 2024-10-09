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
      <template #default="{node}">
        <div class="custom-tree-node">
          <el-tag
            v-if="node.data.name"
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
