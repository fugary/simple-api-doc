<script setup>
import { $copyText } from '@/utils'
import { toEditGroupEnvParams } from '@/utils/DynamicUtils'

const props = defineProps({
  envSuggestions: {
    type: Array,
    default: () => []
  },
  projectId: {
    type: [String, Number],
    default: ''
  },
  preferenceId: {
    type: [String, Number],
    default: ''
  },
  linkClass: {
    type: String,
    default: ''
  },
  linkStyle: {
    type: [String, Object],
    default: ''
  }
})

const openEditWindow = () => {
  const pId = props.projectId || props.preferenceId
  if (pId) {
    toEditGroupEnvParams(pId, { isLocal: true, preferenceId: props.preferenceId || pId })
  }
}
</script>

<template>
  <el-popover
    v-if="envSuggestions?.length || preferenceId || projectId"
    placement="bottom-end"
    title=""
    :width="450"
    trigger="click"
  >
    <template #reference>
      <el-link
        type="primary"
        :class="linkClass"
        :style="linkStyle"
      >
        <span>{{ $t('api.label.variables') }}</span>
      </el-link>
    </template>
    <div
      v-if="preferenceId || projectId"
      style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;"
    >
      <span style="font-weight: 600; font-size: 13px;">{{ $t('api.label.variables') }}</span>
      <el-button
        type="primary"
        link
        size="small"
        @click="openEditWindow"
      >
        <common-icon icon="Edit" /> {{ $t('api.label.editVariables') }}
      </el-button>
    </div>
    <el-table
      :data="envSuggestions"
      size="small"
      border
      max-height="300"
    >
      <el-table-column
        property="name"
        :label="$t('common.label.name')"
        width="130"
      >
        <template #default="{ row }">
          <el-link
            type="primary"
            :underline="false"
            @click="$copyText(row.value)"
          >
            {{ row.name }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column
        property="desc"
        :label="$t('common.label.value')"
      >
        <template #default="{ row }">
          <el-link
            :underline="false"
            style="display: inline-block; vertical-align: bottom; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 250px;"
            @click="$copyText(row.desc)"
          >
            {{ row.desc }}
          </el-link>
        </template>
      </el-table-column>
    </el-table>
  </el-popover>
</template>
