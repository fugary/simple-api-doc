<script setup>
import { computed, watch, ref, nextTick } from 'vue'
import { processSchema, processSchemaChildren, calcComponentMap } from '@/services/api/ApiDocPreviewService'
import SchemaTreeNode from '@/views/components/api/doc/comp/SchemaTreeNode.vue'
import CommonIcon from '@/components/common-icon/index.vue'

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
    const children = processSchemaChildren(processSchema(node.data, componentsMap.value)?.schema, props.showMergeAllOf)
    console.log('========================children', children)
    resolve(children)
  }
}

const treeProps = {
  isLeaf: 'isLeaf'
}

const currentTreeData = ref()
const showEditButtons = (data, node) => {
  return currentTreeData.value === data && !data?.schema?.schema$ref && !node?.parent?.data?.schema?.schema$ref
}

const deleteProperty = (data, parent, $event) => {
  console.log('=======================deleteProperty', data)
  $event.stopPropagation()
}

const currentEdit = ref()
const newOrEdit = (data, parent, $event) => {
  console.log('=======================newOrEdit', data, parent, $event)
  currentEdit.value = data || {}
  $event.stopPropagation()
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
      <template #default="{data,node}">
        <div
          class="form-edit-width-100"
          style="position: relative;"
          @mouseenter="currentTreeData=data"
        >
          <schema-tree-node
            class="form-edit-width-100"
            :data="data"
            :show-merge-all-of="showMergeAllOf"
          />
          <span
            v-if="showEditButtons(data, node)"
            style="position: absolute; top:calc(50% - 11px);right:50px"
          >
            <el-button
              v-if="data.schema.type==='object'"
              v-common-tooltip="$t('common.label.add')"
              type="primary"
              size="small"
              round
              @click="newOrEdit(null, data, $event)"
            >
              <common-icon icon="Plus" />
            </el-button>
            <el-button
              v-if="node.parent"
              v-common-tooltip="$t('common.label.edit')"
              type="primary"
              size="small"
              round
              @click="newOrEdit(data, node.parent, $event)"
            >
              <common-icon icon="Edit" />
            </el-button>
            <el-button
              v-if="node.parent"
              v-common-tooltip="$t('common.label.delete')"
              type="danger"
              size="small"
              round
              @click="deleteProperty(data, data.name, $event)"
            >
              <common-icon icon="DeleteFilled" />
            </el-button>
          </span>
        </div>
      </template>
    </el-tree>
  </el-container>
</template>

<style scoped>

</style>
