<script setup>
import { computed } from 'vue'
import ApiRequestFormAuthorization from '@/views/components/api/form/ApiRequestFormAuthorization.vue'
import ApiEnvPopover from '@/views/components/api/ApiEnvPopover.vue'
import { $i18nBundle } from '@/messages'
import { calcEnvSuggestions } from '@/services/api/ApiCommonService'
import { ElAlert, ElTabs, ElTabPane, ElText } from 'element-plus'

const showWindow = defineModel({
  type: Boolean,
  default: false
})

const authContentModel = defineModel('authContentModel', {
  type: Object,
  default: () => ({ authModels: [] })
})

const props = defineProps({
  title: {
    type: String,
    default: ''
  },
  securitySchemas: {
    type: Object,
    default: () => ({})
  },
  groupConfig: {
    type: Object,
    default: undefined
  },
  preferenceId: {
    type: [String, Number],
    default: undefined
  },
  envSuggestions: {
    type: Array,
    default: undefined
  },
  notice: {
    type: String,
    default: ''
  },
  okClick: {
    type: Function,
    default: undefined
  },
  okLoading: {
    type: Boolean,
    default: false
  },
  clearClick: {
    type: Function,
    default: undefined
  }
})

const calcSuggestions = computed(() => {
  if (props.envSuggestions) return props.envSuggestions
  return calcEnvSuggestions(props.groupConfig, props.preferenceId)
})

const supportedAuthModels = computed(() => {
  return authContentModel.value?.authModels?.filter(authModel => authModel.isSupported) || []
})

const getNotSupportedMsg = (schema) => {
  if (!schema?.isSupported) {
    return $i18nBundle('api.label.notSupported')
  }
  if (!schema?.authModel?.isSupported) {
    return $i18nBundle('api.msg.authNotSupported')
  }
}

const authButtons = computed(() => {
  if (props.clearClick) {
    return [{
      labelKey: 'common.label.clear',
      type: 'success',
      click: props.clearClick
    }]
  }
  return []
})
</script>

<template>
  <common-window
    v-model="showWindow"
    :title="title || $t('api.label.authorization')"
    :ok-click="okClick"
    :ok-loading="okLoading"
    :buttons="authButtons"
    width="800px"
    append-to-body
    destroy-on-close
  >
    <el-container class="flex-column">
      <el-alert
        v-if="notice"
        :title="notice"
        type="warning"
        show-icon
        :closable="false"
        class="margin-bottom2"
      />
      <div style="position: relative;">
        <div style="position: absolute; right: 0; top: 2px; z-index: 10;">
          <ApiEnvPopover
            :env-suggestions="calcSuggestions"
            link-class="margin-top1"
          />
        </div>
        <el-tabs
          v-model="authContentModel.authKeyName"
          class="common-tabs"
        >
          <el-tab-pane
            v-for="(schema, name) in securitySchemas"
            :key="name"
            :disabled="!schema.isSupported||!schema.authModel?.isSupported"
            :name="name"
          >
            <template #label>
              <el-text
                v-common-tooltip="getNotSupportedMsg(schema)"
                type="info"
              >
                {{ name }}
                <span v-if="schema.type">
                  ({{ schema.type }})
                </span>
              </el-text>
            </template>
            <div
              v-if="schema.description"
              class="padding-top2 padding-bottom3"
            >
              <el-text>
                {{ schema.description }}
              </el-text>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
      <common-form
        :model="authContentModel"
        :show-buttons="false"
      >
        <template
          v-for="(authModel, index) in supportedAuthModels"
          :key="authModel.authKeyName"
        >
          <ApiRequestFormAuthorization
            v-if="authContentModel.authKeyName===authModel.authKeyName"
            :model-value="authModel"
            :form-prop="`authModels[${index}]`"
            :show-auth-types="false"
            :group-config="groupConfig"
            :preference-id="preferenceId"
            :supported-auth-types="[authModel.authType]"
          />
        </template>
      </common-form>
    </el-container>
  </common-window>
</template>
