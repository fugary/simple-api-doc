<script setup>
import { computed, watch, ref, nextTick } from 'vue'
import {
  processSchema,
  processSchemaChildren,
  calcComponentMap,
  hasXxxOf,
  calcSchemaPath
} from '@/services/api/ApiDocPreviewService'
import SchemaTreeNode from '@/views/components/api/doc/comp/SchemaTreeNode.vue'
import CommonIcon from '@/components/common-icon/index.vue'
import { toEditJsonSchema } from '@/utils/DynamicUtils'
import { $coreConfirm } from '@/utils'
import { cloneDeep, pull, unset, get, set } from 'lodash-es'
import { $i18nBundle } from '@/messages'

const props = defineProps({
  rootName: {
    type: String,
    default: 'root'
  },
  currentInfoDetail: {
    type: Object,
    required: true
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

const editComponentSchemas = ref()
const componentsMap = computed(() => calcComponentMap(editComponentSchemas.value || props.componentSchemas))

const loadTreeNode = (node, resolve) => {
  if (!node.parent) { // 第一级
    let firstArr = [processSchema(cloneDeep(schemaModel.value), componentsMap.value)]
    firstArr = firstArr.map(data => {
      const xxxOf = hasXxxOf(data)
      return {
        id: '_root',
        name: props.rootName,
        xxxOf,
        schema$ref: data?.schema$ref,
        schema: data
      }
    })
    console.log('========================firstArr', firstArr)
    resolve(firstArr)
  } else {
    // 下级node
    const children = processSchemaChildren(processSchema(node.data, componentsMap.value)?.schema, props.showMergeAllOf)
    console.log('========================children', children)
    children.forEach((child, index) => calcSchemaPath(child, node.data, index))
    resolve(children)
  }
}

const treeProps = {
  isLeaf: 'isLeaf'
}

const currentTreeData = ref()

const deleteProperty = (data, parent, $event) => {
  if (data.path) {
    $coreConfirm($i18nBundle('common.msg.deleteConfirm')).then(() => {
      unset(schemaModel.value, data.path)
      console.log('============delete', data, parent, schemaModel.value)
      const parentModel = data.parentPath ? get(schemaModel.value, data.parentPath) : schemaModel.value
      const xxxOf = parent?.xxxOf
      if (xxxOf && parentModel[xxxOf]) {
        parentModel[xxxOf] = parentModel[xxxOf].filter(item => !!item)
        if (!parentModel[xxxOf].length) {
          delete parentModel[xxxOf]
        }
      }
      if (parentModel.required?.length) {
        pull(parentModel.required, data.name)
      }
    })
  }
  $event.stopPropagation()
}

const currentEdit = ref()
const newOrEdit = (data, parent, $event) => {
  console.log('=======================newOrEdit', data?.path, data, parent, $event)
  if (data) {
    data = cloneDeep(data)
    data.schema = cloneDeep(data?.path ? get(schemaModel.value, data.path) : schemaModel.value)
  } else {
    data = { schema: { type: 'string' } }
  }
  currentEdit.value = data
  toEditJsonSchema(data, props.currentInfoDetail, {
    onSaveJsonSchema (toSaveData) {
      const newData = calcSchemaPath(cloneDeep(toSaveData), parent, 0)
      console.log('==============toSaveData', toSaveData, newData)
      if (data.path && newData.path !== data.path) { // 改了属性名称，需要删除原数据
        const parentPropPath = data.path.substring(0, data.path.lastIndexOf('.'))
        const properties = get(schemaModel.value, parentPropPath)
        const newProperties = Object.entries(properties || {}).reduce((res, [key, value]) => {
          if (data.path === parentPropPath + '.' + key) {
            res[newData.name] = newData.schema
          } else {
            res[key] = value
          }
          return res
        }, {})
        console.log('==============parentPropPath', parentPropPath, properties, newProperties)
        set(schemaModel.value, parentPropPath, newProperties)
      } else {
        if (newData.path) {
          set(schemaModel.value, newData.path, newData.schema)
        } else {
          schemaModel.value = newData.schema
        }
      }
    },
    onEditComponentSchemas (componentSchemas) {
      editComponentSchemas.value = componentSchemas
    }
  })
  $event.stopPropagation()
}

const checkRefRelated = node => { // 判断上级是否是ref
  let res = false
  while (node?.level !== 0) { // 不是第一级,循环验证
    if (node.data?.schema$ref) { // || node.data?.xxxOf
      res = true
      break
    }
    node = node.parent
  }
  return res
}

const checkAddProperty = node => {
  const schema = node.data?.schema
  return (schema?.type === 'object' || schema?.properties) && !checkRefRelated(node) && !node.data?.xxxOf// 当前以及上级都没有ref
}

const checkEditProperty = node => !checkRefRelated(node.parent) // 上级中没有ref

const checkDeleteProperty = node => node.level > 1 && !checkRefRelated(node.parent)

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
          @mouseleave="currentTreeData=null"
        >
          <schema-tree-node
            class="form-edit-width-100"
            :data="data"
            :show-merge-all-of="showMergeAllOf"
          />
          <span
            v-if="currentTreeData===data"
            style="position: absolute; top:calc(50% - 11px);right:50px"
          >
            <el-button
              v-if="checkAddProperty(node)"
              v-common-tooltip="$t('common.label.add')"
              type="primary"
              size="small"
              round
              @click="newOrEdit(null, data, $event)"
            >
              <common-icon icon="Plus" />
            </el-button>
            <el-button
              v-if="checkEditProperty(node)"
              v-common-tooltip="$t('common.label.edit')"
              type="primary"
              size="small"
              round
              @click="newOrEdit(data, node.parent?.data, $event)"
            >
              <common-icon icon="Edit" />
            </el-button>
            <el-button
              v-if="checkDeleteProperty(node)"
              v-common-tooltip="$t('common.label.delete')"
              type="danger"
              size="small"
              round
              @click="deleteProperty(data, node.parent?.data, $event)"
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
