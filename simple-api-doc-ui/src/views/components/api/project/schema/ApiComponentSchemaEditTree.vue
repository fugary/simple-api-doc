<script setup>
import { computed, watch, ref, nextTick } from 'vue'
import { processSchema, processSchemaChildren, calcComponentMap } from '@/services/api/ApiDocPreviewService'
import SchemaTreeNode from '@/views/components/api/doc/comp/SchemaTreeNode.vue'

const props = defineProps({
  rootName: {
    type: String,
    default: 'root'
  },
  componentSchemas: {
    type: Array,
    default: () => ([])
  },
  specVersion: {
    type: String,
    default: 'V30'
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

const showTree = ref(false)
watch([schemaModel, () => props.rootName], () => {
  showTree.value = false
  nextTick(() => {
    showTree.value = true
  })
}, { immediate: true })

const componentsMap = computed(() => calcComponentMap(props.componentSchemas))

const loadTreeNode = (node, resolve) => {
  if (!node.parent) { // 第一级
    let firstArr = [processSchema(schemaModel.value, componentsMap.value)]
    firstArr = firstArr.map(data => {
      return {
        id: '_root',
        name: props.rootName,
        schema: data
      }
    })
    console.log('========================firstArr', firstArr)
    resolve(firstArr)
  } else {
    // 下级node
    resolve(processSchemaChildren(processSchema(node.data, componentsMap.value)?.schema, props.showMergeAllOf))
  }
}

const treeProps = {
  isLeaf: 'isLeaf'
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
        <div class="text-left padding-10">
          <el-text type="info">
            {{ $t('common.msg.noData') }}
          </el-text>
        </div>
      </template>
      <template #default="{data}">
        <schema-tree-node
          :data="data"
          :show-merge-all-of="showMergeAllOf"
        />
      </template>
    </el-tree>
  </el-container>
</template>

<style scoped>

</style>
