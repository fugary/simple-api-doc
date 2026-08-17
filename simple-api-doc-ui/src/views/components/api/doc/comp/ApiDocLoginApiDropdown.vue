<script setup>
import { computed } from 'vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import { openLoginApiDebug } from '@/services/api/ApiDocPreviewService'
import { useShareConfigStore } from '@/stores/ShareConfigStore'

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

const shareConfigStore = useShareConfigStore()

const getApiStatus = (api) => {
  const prefId = props.paramTarget?.preferenceId
  const statusMap = (prefId && shareConfigStore.sharePreferenceView[prefId]?.docStatusMap) || {}
  const docStatus = api?.apiId ? statusMap[String(api.apiId)] : null
  const enabled = docStatus ? docStatus.enabled : (api.enabled !== false && api.status !== 0 && api.deleted !== true)
  const deprecated = docStatus ? docStatus.deprecated : Boolean(api.deprecated)
  return { enabled, deprecated }
}

const validLoginApiConfigs = computed(() => {
  return (props.loginApiConfigs || []).filter(api => {
    if (!api) return false
    return getApiStatus(api).enabled
  })
})

const showDropdown = computed(() => !props.isCurrentLoginApi && validLoginApiConfigs.value.length > 1)
const showSingleLink = computed(() => !props.isCurrentLoginApi && validLoginApiConfigs.value.length === 1)

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
            v-for="api in validLoginApiConfigs"
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
              <el-text
                :type="getApiStatus(api).deprecated ? 'warning' : ''"
                style="display: inline-flex; align-items: center; gap: 8px; flex: 1; min-width: 0;"
              >
                <component
                  :is="getApiStatus(api).deprecated ? 'del' : 'span'"
                  style="font-weight: 500; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;"
                  :title="api.summary || api.url"
                >
                  {{ api.summary || api.url }}
                </component>
                <component
                  :is="getApiStatus(api).deprecated ? 'del' : 'span'"
                  v-if="api.url"
                  class="dropdown-api-url"
                  style="font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-left: auto;"
                  :title="api.url"
                >
                  {{ api.url }}
                </component>
              </el-text>
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
      @click="handleOpenLoginApiDebug(validLoginApiConfigs[0])"
    >
      <span>{{ $t('api.label.loginApi') }}</span>
    </el-link>
  </template>
</template>

<style scoped>
.dropdown-api-url {
  color: var(--el-text-color-secondary);
}
.el-text--warning .dropdown-api-url {
  color: inherit;
  opacity: 0.85;
}
</style>
