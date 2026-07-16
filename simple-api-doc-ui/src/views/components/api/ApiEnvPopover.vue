<script setup>
import { $copyText } from '@/utils'

defineProps({
  envSuggestions: {
    type: Array,
    default: () => []
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
</script>

<template>
  <el-popover
    v-if="envSuggestions?.length"
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
    <el-table
      :data="envSuggestions"
      size="small"
      border
    >
      <el-table-column
        property="name"
        :label="$t('common.label.name')"
        width="120"
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
            style="display: inline-block; vertical-align: bottom; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 280px;"
            @click="$copyText(row.desc)"
          >
            {{ row.desc }}
          </el-link>
        </template>
      </el-table-column>
    </el-table>
  </el-popover>
</template>
