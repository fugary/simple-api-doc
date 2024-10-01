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
  }
})

const calcHandlers = computed(() => {
  return props.handlers.filter(item => item.enabled !== false)
})

</script>

<template>
  <el-dropdown>
    <el-link :underline="false">
      <el-tag
        type="info"
        effect="light"
      >
        <common-icon :icon="icon" />
      </el-tag>
    </el-link>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="(handler, index) in calcHandlers"
          :key="index"
          :disabled="handler.disabled"
          :divided="handler.divided"
          @click="handler.handler"
        >
          <el-link
            :underline="false"
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
