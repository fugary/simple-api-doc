<script setup>
import { computed } from 'vue'
const props = defineProps({
  icon: {
    type: String,
    default: 'MoreFilled'
  },
  handlers: {
    type: Array,
    default: () => []
  },
  iconSize: {
    type: Number,
    default: 20
  }
})

const calcHandlers = computed(() => {
  return props.handlers.filter(item => item.enabled !== false)
})

defineEmits(['enterDropdown', 'leaveDropdown', 'showDropdown'])

</script>

<template>
  <el-dropdown @visible-change="$event&&$emit('showDropdown')">
    <el-link underline="never">
      <el-link
        type="info"
        underline="never"
      >
        <common-icon
          :size="iconSize"
          :icon="icon"
        />
      </el-link>
    </el-link>
    <template #dropdown>
      <el-dropdown-menu
        @mouseenter="$emit('enterDropdown');"
        @mouseleave="$emit('leaveDropdown');"
      >
        <el-dropdown-item
          v-for="(handler, index) in calcHandlers"
          :key="index"
          :disabled="handler.disabled"
          :divided="handler.divided"
          @click="handler.handler"
        >
          <el-link
            underline="never"
            :type="handler.type || 'default'"
          >
            <common-icon
              :icon="handler.icon"
            />
            {{ handler.label || $t(handler.labelKey) }}
          </el-link>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>

</style>
