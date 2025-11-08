<script setup>
import { computed, watch, ref, nextTick } from 'vue'
import { processSchema, processSchemaChildren, calcComponentMap } from '@/services/api/ApiDocPreviewService'
import SchemaTreeNode from '@/views/components/api/doc/comp/SchemaTreeNode.vue'
import CommonIcon from '@/components/common-icon/index.vue'
import { toEditJsonSchema } from '@/utils/DynamicUtils'
import { $coreConfirm } from '@/utils'
import { pull, unset } from 'lodash-es'
import { $i18nBundle } from '@/messages'

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

const defaultExpandedKeys = ref(['_root'])
const toggleExpandKey = (data, expand) => {
  console.log('============expand', data, expand)
  if (expand) {
    defaultExpandedKeys.value.push(data.id)
  } else {
    pull(defaultExpandedKeys.value, data.id)
  }
}

const showTree = ref(false)
watch([schemaModel, () => props.rootName], () => {
  showTree.value = false
  nextTick(() => {
    showTree.value = true
  })
}, { immediate: true, deep: true })

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
    console.log('========================children', node, children)
    children.forEach(child => {
      child.path = `properties.${child.name}`
      if (node.data?.path) {
        child.parentPath = node.data?.path
        child.path = `${node.data?.path}.${child.path}`
      }
      child.id = child.path
    })
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

const deleteProperty = (data, $event) => {
  if (data.path) {
    $coreConfirm($i18nBundle('common.msg.deleteConfirm')).then(() => {
      unset(schemaModel.value, data.path)
      const parent = data.parentPath ? schemaModel.value[data.parentPath] : schemaModel.value
      if (parent.required?.length) {
        pull(parent.required, data.name)
      }
    })
  }
  $event.stopPropagation()
}

const currentEdit = ref()
const newOrEdit = (data, parent, $event) => {
  console.log('=======================newOrEdit', data, parent, $event)
  currentEdit.value = data || {}
  toEditJsonSchema(data)
  $event.stopPropagation()
}

</script>

<template>
  <el-container class="flex-column">
    <el-tree
      v-if="showTree"
      :props="treeProps"
      class="doc-schema-tree"
      :default-expanded-keys="defaultExpandedKeys"
      node-key="id"
      lazy
      :load="loadTreeNode"
      @node-expand="toggleExpandKey($event, true)"
      @node-collapse="toggleExpandKey($event, false)"
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
              v-if="data.schema?.type==='object'||data.schema?.properties"
              v-common-tooltip="$t('common.label.add')"
              type="primary"
              size="small"
              round
              @click="newOrEdit(null, data, $event)"
            >
              <common-icon icon="Plus" />
            </el-button>
            <el-button
              v-if="node.parent&&node.level>1"
              v-common-tooltip="$t('common.label.edit')"
              type="primary"
              size="small"
              round
              @click="newOrEdit(data, node.parent, $event)"
            >
              <common-icon icon="Edit" />
            </el-button>
            <el-button
              v-if="node.parent&&node.level>1"
              v-common-tooltip="$t('common.label.delete')"
              type="danger"
              size="small"
              round
              @click="deleteProperty(data, $event)"
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
