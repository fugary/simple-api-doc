<script setup>
import { computed, watch, ref, nextTick } from 'vue'
import {
  processSchema,
  processSchemaChildren,
  calcComponentMap,
  hasXxxOf,
  processSchemaProperties,
  processSchemaRequired,
  calcSchemaPath, $ref2Schema
} from '@/services/api/ApiDocPreviewService'
import SchemaTreeNode from '@/views/components/api/doc/comp/SchemaTreeNode.vue'
import CommonIcon from '@/components/common-icon/index.vue'
import { showMarkdownWindow, toEditComponent, toEditJsonSchema } from '@/utils/DynamicUtils'
import { $coreConfirm, getSingleSelectOptions } from '@/utils'
import { cloneDeep, pull, unset, get, set } from 'lodash-es'
import { $i18nBundle, $i18nKey } from '@/messages'
import { SCHEMA_BASE_TYPES, SCHEMA_COMPONENT_PREFIX } from '@/consts/ApiConstants'
import { useComponentSchemas } from '@/services/api/ApiDocEditService'
import { defineFormOptions } from '@/components/utils'

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

const { componentSchemas: editComponentSchemas, loadComponents } = useComponentSchemas()
const componentsMap = computed(() => calcComponentMap(editComponentSchemas.value?.length ? editComponentSchemas.value : props.componentSchemas))

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

const deleteProperty = (data, parent) => {
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
}

const currentEdit = ref()
const newOrEdit = (data, parent) => {
  console.log('=======================newOrEdit', data?.path, data, parent)
  data = initEditData(data)
  currentEdit.value = data
  toEditJsonSchema(data, props.currentInfoDetail, {
    onSaveJsonSchema (toSaveData) {
      saveEditSchemaData(data, parent, toSaveData)
    },
    onEditComponentSchemas (componentSchemas) {
      editComponentSchemas.value = componentSchemas
    }
  })
}

const initEditData = (data) => {
  if (data) {
    data = cloneDeep(data)
    data.schema = cloneDeep(data?.path ? get(schemaModel.value, data.path) : schemaModel.value)
  } else {
    data = { schema: { type: 'string' } }
  }
  return data
}

const saveEditSchemaData = (data, parent, toSaveData) => {
  const newData = calcSchemaPath(cloneDeep(toSaveData), parent, 0)
  console.log('==============toSaveData', toSaveData, newData)
  if (data.path && newData.path !== data.path) { // 改了属性名称，需要删除原数据
    processSchemaProperties(data, newData, schemaModel)
  } else if (newData.path) {
    set(schemaModel.value, newData.path, newData.schema)
  } else {
    schemaModel.value = newData.schema
  }
  processSchemaRequired(data, newData, schemaModel)
}

const getData$ref = data => {
  return data?.schema$ref || data?.schema?.items?.schema$ref
}

const checkRefRelated = node => { // 判断上级是否是ref
  let res = false
  while (node?.level !== 0) { // 不是第一级,循环验证
    if (getData$ref(node.data)) { // || node.data?.xxxOf
      res = true
      break
    }
    node = node.parent
  }
  return res
}

const checkNotXxxOf = node => !node?.data?.xxxOf

const checkNotAdditional = node => !node?.data?.isAdditional

const isObjectSchema = schema => schema?.type === 'object' || (!schema?.type && schema?.properties)

const checkAddProperty = node => {
  const schema = node.data?.schema
  return (isObjectSchema(schema) || isObjectSchema(schema?.items)) && !checkRefRelated(node) && checkNotXxxOf(node)// 当前以及上级都没有ref
}

const checkEditProperty = node => !checkRefRelated(node.parent) && checkNotXxxOf(node.parent) && checkNotAdditional(node) // 上级中没有ref

const checkDeleteProperty = node => node.level > 1 && !checkRefRelated(node.parent)

const checkGotoRef = node => {
  const $ref = getData$ref(node.data)
  return $ref && componentsMap.value[$ref]
}

const saveToSchemaModel = (data, schema) => {
  if (data?.schema?.items?.schema$ref && schemaModel.value?.items) {
    if (data.path) {
      set(schemaModel.value.items, data.path, schema)
    } else {
      schemaModel.value.items = schema
    }
  } else {
    if (data.path) {
      set(schemaModel.value, data.path, schema)
    } else {
      schemaModel.value = schema
    }
  }
}

const dereferenceSchema = data => {
  const $ref = getData$ref(data)
  const unRefMsg = $i18nBundle('api.label.deRef', [$ref2Schema($ref) || $i18nBundle('api.label.typeRef')])
  $coreConfirm($i18nBundle('common.msg.commonConfirm', [unRefMsg])).then(() => {
    const component = componentsMap.value[getData$ref(data)]
    if (component?.schema) {
      const schema = cloneDeep(component.schema)
      console.log('=============================dereference schema', schemaModel.value, data, schema)
      saveToSchemaModel(data, schema)
    }
  })
}

const constructRefSchema = data => {
  $coreConfirm($i18nBundle('api.label.constructRef')).then(() => {
    const editSchema = data.path ? get(schemaModel.value, data.path) : schemaModel.value
    toEditComponent({
      projectId: props.currentInfoDetail.projectId,
      infoId: props.currentInfoDetail.infoId,
      schemaContent: JSON.stringify(editSchema),
      bodyType: 'component'
    }, {
      onSaveComponent: (newData) => {
        console.log('==================================', data, newData)
        loadComponents(props.currentInfoDetail).then(() => {
          saveToSchemaModel(data, { $ref: `${SCHEMA_COMPONENT_PREFIX}${newData.schemaName}` })
        })
      }
    })
  })
}

const baseNodeCheck = node => node?.data?.id !== '_root' && checkEditProperty(node)

const allowDrop = (draggingNode, dropNode, type) => {
  return baseNodeCheck(dropNode) && (type !== 'inner' || isObjectSchema(dropNode.data?.schema))
}
const allowDrag = (draggingNode) => {
  return baseNodeCheck(draggingNode) // 可编辑的非根节点
}
const handleDragEnd = (draggingNode, dropNode, type) => {
  if (dropNode) {
    const parentNode = type === 'inner' ? dropNode : dropNode.parent
    const childNodes = [...parentNode.childNodes]
    childNodes.forEach(node => {
      let data = node.data
      const schema = get(schemaModel.value, data.path) // 从schemaModel中提取schema原始数据
      unset(schemaModel.value, data.path) // 清理schema数据
      if (data.id === draggingNode.data.id) { // 如果是移动节点可能父节点会变化，重新计算路径值
        data = calcSchemaPath(cloneDeep(draggingNode.data), parentNode.data, 0)
      }
      console.log('========================node', data.path, node, schema)
      saveToSchemaModel(data, cloneDeep(schema)) // 设置值
    })
  }
}

const currentModel = ref()
const inlineEditOptions = computed(() => {
  const typeOptions = getSingleSelectOptions(...SCHEMA_BASE_TYPES)
  return defineFormOptions([
    { labelKey: 'common.label.name', prop: 'name', required: true, showLabel: false },
    { labelKey: 'api.label.required', prop: 'required', type: 'switch', labelWidth: 'auto' },
    { labelKey: 'api.label.typeBasic', prop: 'schema.type', required: true, children: typeOptions, type: 'select', showLabel: false },
    {
      labelKey: 'common.label.description',
      prop: 'schema.description',
      showLabel: false,
      attrs: {
        onDblclick () {
          showMarkdownWindow({
            content: currentModel.value?.schema?.description,
            title: $i18nKey('common.label.commonEdit', 'common.label.description')
          }, {
            'onUpdate:modelValue': value => {
              if (currentModel.value?.schema) {
                currentModel.value.schema.description = value
              }
            }
          })
        }
      }
    }
  ])
})

const toEditSchemaModel = (node) => {
  if (checkNotXxxOf(node) && !checkRefRelated(node)) {
    currentModel.value = initEditData(node.data)
  } else {
    newOrEdit(node.data, node.parent?.data)
  }
}

const saveInlineEditSchemaData = (form, data, parent) => {
  form.validate().then(valid => {
    if (valid) {
      saveEditSchemaData(data, parent, currentModel.value)
      currentModel.value = null
    }
  })
}

defineEmits(['gotoComponent'])

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
      :allow-drag="allowDrag"
      :allow-drop="allowDrop"
      draggable
      @node-drag-end="handleDragEnd"
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
            v-if="!currentModel||currentModel.id!==data.id"
            class="form-edit-width-100"
            :data="data"
            :show-merge-all-of="showMergeAllOf"
          />
          <common-form
            v-else
            inline
            class-name="common-form-auto"
            :model="currentModel"
            :options="inlineEditOptions"
            :show-submit="false"
            @click.stop
          >
            <template #buttons="{form}">
              <el-button
                v-common-tooltip="$t('common.label.edit')"
                type="primary"
                size="small"
                round
                @click.stop="saveInlineEditSchemaData(form, data, node.parent?.data)"
              >
                <common-icon
                  icon="Check"
                  :size="18"
                />
              </el-button>
              <el-button
                v-if="checkEditProperty(node)"
                v-common-tooltip="$t('common.label.newWindowEdit')"
                type="primary"
                size="small"
                round
                @click.stop="newOrEdit(data, node.parent?.data)"
              >
                <common-icon
                  icon="Edit"
                  :size="18"
                />
              </el-button>
              <el-button
                v-common-tooltip="$t('common.label.cancel')"
                size="small"
                round
                @click.stop="currentModel=null"
              >
                <common-icon
                  icon="CloseBold"
                  :size="18"
                />
              </el-button>
            </template>
          </common-form>
          <span
            v-if="currentTreeData===data&&currentModel?.id!==data.id"
            style="position: absolute; top:calc(50% - 11px);right:50px"
          >
            <el-button
              v-if="checkAddProperty(node)"
              v-common-tooltip="$i18nKey('common.label.commonAdd1', 'api.label.property')"
              type="primary"
              size="small"
              round
              @click.stop="newOrEdit(null, data)"
            >
              <common-icon
                icon="Plus"
                :size="18"
              />
            </el-button>
            <el-button
              v-if="checkGotoRef(node)"
              v-common-tooltip="$i18nBundle('common.label.commonGoto', [$ref2Schema(getData$ref(data))||$t('api.label.typeRef')])"
              type="success"
              size="small"
              round
              @click.stop="$emit('gotoComponent', componentsMap[getData$ref(data)]);"
            >
              <common-icon
                icon="ManageSearchFilled"
                :size="18"
              />
            </el-button>
            <el-button
              v-if="checkGotoRef(node)&&!checkGotoRef(node.parent)"
              v-common-tooltip="$i18nBundle('api.label.deRef', [$ref2Schema(getData$ref(data))||$t('api.label.typeRef')])"
              type="danger"
              size="small"
              round
              @click.stop="dereferenceSchema(data);"
            >
              <common-icon
                icon="LinkOffFilled"
                :size="18"
              />
            </el-button>
            <el-button
              v-if="!checkGotoRef(node)&&data.schema?.type==='object'"
              v-common-tooltip="$i18nBundle('api.label.constructRef')"
              type="success"
              size="small"
              round
              @click.stop="constructRefSchema(data);"
            >
              <common-icon
                icon="LinkFilled"
                :size="18"
              />
            </el-button>
            <el-button
              v-if="checkEditProperty(node)"
              v-common-tooltip="$t('common.label.edit')"
              type="primary"
              size="small"
              round
              @click.stop="toEditSchemaModel(node)"
            >
              <common-icon
                icon="Edit"
                :size="18"
              />
            </el-button>
            <el-button
              v-if="checkDeleteProperty(node)"
              v-common-tooltip="$t('common.label.delete')"
              type="danger"
              size="small"
              round
              @click.stop="deleteProperty(data, node.parent?.data)"
            >
              <common-icon
                icon="DeleteFilled"
                :size="18"
              />
            </el-button>
          </span>
        </div>
      </template>
    </el-tree>
  </el-container>
</template>

<style scoped>

</style>
