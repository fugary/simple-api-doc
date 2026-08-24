<script setup>
import { computed, reactive, ref } from 'vue'
import { removeByQuery } from '@/api/ApiProjectInfoDetailApi'
import { $i18nBundle } from '@/messages'
import { ElMessage } from 'element-plus'

const props = defineProps({
  projectItem: {
    type: Object,
    default: null
  },
  searchParam: {
    type: Object,
    default: () => ({})
  },
  stat: {
    type: Object,
    default: () => ({ totalCount: 0, lockedCount: 0, unlockedCount: 0 })
  }
})

const emit = defineEmits(['success'])

const showWindow = defineModel('modelValue', {
  type: Boolean,
  default: false
})

const formData = reactive({
  keepLocked: true
})
const loading = ref(false)

const formOptions = computed(() => {
  const options = []
  if ((props.stat?.lockedCount || 0) > 0) {
    options.push({
      labelKey: 'api.label.keepLockedModels',
      prop: 'keepLocked',
      type: 'switch'
    })
  }
  return options
})

const isClearDisabled = computed(() => {
  if (loading.value) {
    return true
  }
  if (formData.keepLocked && (props.stat?.unlockedCount || 0) === 0) {
    return true
  }
  return (props.stat?.totalCount || 0) === 0
})

const alertType = computed(() => {
  if (formData.keepLocked || (props.stat?.lockedCount || 0) === 0) {
    return 'warning'
  }
  return 'error'
})

const alertTitle = computed(() => {
  const total = props.stat?.totalCount || 0
  const locked = props.stat?.lockedCount || 0
  const unlocked = props.stat?.unlockedCount || 0

  if (locked === 0) {
    return $i18nBundle('api.msg.clearComponentsConfirm', [total])
  }
  if (formData.keepLocked) {
    if (unlocked > 0) {
      return $i18nBundle('api.msg.clearComponentsDynamicNotice', [unlocked, locked])
    }
    return $i18nBundle('api.msg.allComponentsLockedNotice')
  }
  return $i18nBundle('api.msg.clearComponentsForceWarning', [total, locked])
})

const doClear = async () => {
  loading.value = true
  try {
    const res = await removeByQuery({
      ...props.searchParam,
      keepLocked: formData.keepLocked,
      checkOnly: false
    }, { loading: true })
    if (res?.success) {
      ElMessage.success($i18nBundle('common.msg.deleteSuccess'))
      showWindow.value = false
      emit('success', res.resultData)
    }
  } finally {
    loading.value = false
  }
}

const actionButtons = computed(() => [
  {
    label: $i18nBundle('api.label.clearComponents'),
    type: 'danger',
    disabled: isClearDisabled.value,
    loading: loading.value,
    click: doClear
  }
])
</script>

<template>
  <common-window
    v-model="showWindow"
    :title="$t('api.label.clearComponents')"
    width="520px"
    append-to-body
    destroy-on-close
    :show-ok="false"
    :show-cancel="true"
    :cancel-label="$t('common.label.cancel')"
    :buttons="actionButtons"
  >
    <common-form
      :model="formData"
      :options="formOptions"
      :show-buttons="false"
      label-width="160px"
      class="form-edit-width-100"
    >
      <el-alert
        :title="alertTitle"
        :type="alertType"
        :closable="false"
        show-icon
        class="margin-top1 margin-bottom1"
      />
    </common-form>
  </common-window>
</template>

<style scoped>
</style>
