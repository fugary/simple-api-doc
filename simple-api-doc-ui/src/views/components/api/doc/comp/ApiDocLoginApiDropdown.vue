<script setup>
import { computed } from 'vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import { openLoginApiDebug } from '@/services/api/ApiDocPreviewService'

const props = defineProps({
  loginApiConfigs: {
    type: Array,
    default: () => []
  },
  isCurrentLoginApi: {
    type: Boolean,
    default: false
  },
  paramTarget: {
    type: Object,
    default: undefined
  },
  linkClass: {
    type: [String, Object, Array],
    default: ''
  },
  linkStyle: {
    type: [String, Object, Array],
    default: ''
  }
})

const showDropdown = computed(() => !props.isCurrentLoginApi && props.loginApiConfigs?.length > 1)
const showSingleLink = computed(() => !props.isCurrentLoginApi && props.loginApiConfigs?.length === 1)

const handleOpenLoginApiDebug = (config) => {
  if (config) {
    openLoginApiDebug(config, props.paramTarget)
  }
}
</script>

<template>
  <template v-if="showDropdown">
    <el-dropdown
      trigger="hover"
      :class="linkClass"
      :style="linkStyle"
      @command="handleOpenLoginApiDebug"
    >
      <el-link
        type="primary"
        :underline="false"
      >
        <span>{{ $t('api.label.loginApi') }}</span>
      </el-link>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="api in loginApiConfigs"
            :key="api.apiId"
            :command="api"
          >
            <div style="display: flex; align-items: center; gap: 8px; max-width: 450px;">
              <ApiMethodTag
                v-if="api.method"
                :method="api.method"
                size="small"
                style="flex-shrink: 0;"
              />
              <span
                style="font-weight: 500; font-size: 13px; color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap;"
                :title="api.summary || api.url"
              >
                {{ api.summary || api.url }}
              </span>
              <span
                v-if="api.url"
                style="font-size: 12px; color: var(--el-text-color-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-left: auto;"
                :title="api.url"
              >
                {{ api.url }}
              </span>
            </div>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </template>
  <template v-else-if="showSingleLink">
    <el-link
      type="primary"
      :underline="false"
      :class="linkClass"
      :style="linkStyle"
      @click="handleOpenLoginApiDebug(loginApiConfigs[0])"
    >
      <span>{{ $t('api.label.loginApi') }}</span>
    </el-link>
  </template>
</template>
