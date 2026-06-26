<script setup>
import { $i18nKey } from '@/messages'

defineProps({
  title: {
    type: String,
    default: ''
  },
  examples: {
    type: Array,
    default: () => []
  },
  readOnly: {
    type: Boolean,
    default: false
  }
})
const emit = defineEmits(['selectExample', 'deleteExample'])
</script>

<template>
  <el-dropdown>
    <el-link
      v-common-tooltip="title||$i18nKey('common.label.commonView', 'common.label.example')"
      type="primary"
      underline="never"
      class="margin-left3"
    >
      <common-icon
        :size="18"
        icon="ListAltFilled"
      />
    </el-link>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="(example, index) in examples"
          :key="index"
          @click="emit('selectExample', example)"
        >
          <div style="display: flex; align-items: center; justify-content: space-between; width: 100%;">
            <span class="margin-right3">{{ example.summary || example.description }}</span>
            <el-link
              v-if="!readOnly"
              type="danger"
              :underline="false"
              @click.stop="emit('deleteExample', example, index)"
            >
              <common-icon
                icon="DeleteFilled"
                :size="14"
              />
            </el-link>
          </div>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>

</style>
