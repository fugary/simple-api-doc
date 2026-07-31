<script setup>
import { computed, ref } from 'vue'
import { toLabelByKey } from '@/components/utils'
import { useRenderKey, useSortableParams } from '@/hooks/CommonHooks'

const props = defineProps({
  formOptions: {
    type: Array,
    default: () => []
  },
  model: {
    type: Object,
    required: true
  },
  dataListKey: {
    type: String,
    default: 'items'
  },
  showOperation: {
    type: Boolean,
    default: true
  },
  operationWidth: {
    type: String,
    default: '110px'
  },
  formPropPrefix: {
    type: String,
    default: ''
  },
  sortable: {
    type: Boolean,
    default: false
  }
})

const dataList = computed(() => {
  return props.model[props.dataListKey] || []
})

const emit = defineEmits(['delete', 'change'])

const { renderKey } = useRenderKey()
const rowKey = (row) => renderKey(row)

const sortableState = props.sortable ? useSortableParams(dataList, '.el-table__row') : {}
const sortableRef = sortableState.sortableRef || ref()

const deleteItem = (item, index) => {
  emit('delete', {
    item, index
  })
}

const formChange = ($event, row, $index, option) => {
  const args = [$event, {
    model: row,
    index: $index,
    option
  }]
  if (option.formChange) { // 动态表单change事件
    option.formChange(...args)
  }
  emit('change', ...args)
}

const options = computed(() => {
  return props.formOptions.filter(option => option.enabled !== false)
})

</script>

<template>
  <el-table
    ref="sortableRef"
    :data="dataList"
    :row-key="rowKey"
    class="common-table-form"
  >
    <el-table-column
      v-if="sortable"
      width="40px"
      align="center"
    >
      <template #default>
        <div
          class="el-form-item"
          style="display: flex; align-items: center; justify-content: center; height: 32px;"
        >
          <common-icon
            icon="DragIndicatorFilled"
            class="move-indicator"
            style="cursor: move; color: var(--el-text-color-secondary);"
          />
        </div>
      </template>
    </el-table-column>
    <el-table-column
      v-for="(option, index) in options"
      :key="`${option.prop}__${index}`"
      :width="option.width"
      :min-width="option.minWidth"
      v-bind="option.columnAttrs"
    >
      <template
        v-if="option.headerSlot"
        #header="scope"
      >
        <slot
          v-bind="scope"
          :name="option.headerSlot"
        />
      </template>
      <template
        v-else
        #header
      >
        <el-form-item
          :label="option.label||toLabelByKey(option.labelKey)"
          class="common-table-form-label"
          :required="option.required"
        />
      </template>
      <!--用于自定义显示属性-->
      <template
        v-if="option.slot"
        #default="scope"
      >
        <slot
          v-bind="scope"
          :item="scope.row"
          :name="option.slot"
        />
      </template>
      <template
        v-else
        #default="{row, $index}"
      >
        <common-form-control
          :model="row"
          label-width="0"
          :option="option"
          :prop="formPropPrefix ? `${formPropPrefix}.${dataListKey}.${$index}.${option.prop}` : `${dataListKey}.${$index}.${option.prop}`"
          @change="formChange($event, row, $index, option)"
        />
      </template>
    </el-table-column>
    <el-table-column
      v-if="showOperation"
      :width="operationWidth"
      :label="$t('common.label.operation')"
    >
      <template #default="{row, $index}">
        <div class="el-form-item">
          <el-button
            circle
            type="danger"
            size="small"
            underline="never"
            @click="deleteItem(row, $index)"
          >
            <common-icon
              icon="Delete"
            />
          </el-button>
        </div>
      </template>
    </el-table-column>
    <template #empty="scope">
      <slot
        name="empty"
        v-bind="scope"
      />
    </template>
    <template #append="scope">
      <slot
        name="append"
        v-bind="scope"
      />
    </template>
  </el-table>
</template>

<style scoped>

</style>
