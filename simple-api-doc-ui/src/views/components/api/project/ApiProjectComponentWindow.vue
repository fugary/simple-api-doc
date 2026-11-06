<script setup lang="jsx">
import { ref, useTemplateRef, computed } from 'vue'
import ApiProjectComponent from '@/views/components/api/project/ApiProjectComponent.vue'
import { $i18nKey } from '@/messages'
const showWindow = ref(false)
const currentInfo = ref()
defineOptions({
  inheritAttrs: false
})
const toEditComponent = (component) => {
  currentInfo.value = component
  showWindow.value = true
}
const apiComponentRef = useTemplateRef('apiComponent')
const saveComponent = ({ form }) => {
  apiComponentRef.value?.saveComponent(form).then(saved => {
    if (saved) showWindow.value = false
  })
  return false
}
const closeComponent = () => {
  Promise.resolve(apiComponentRef.value?.draft.confirmLeave() ?? true).then(leave => {
    if (leave) showWindow.value = false
  })
  return false
}
defineExpose({
  toEditComponent
})
const buttons = computed(() => {
  return [{
    labelKey: 'common.label.save',
    type: 'primary',
    click ({ form }) {
      apiComponentRef.value?.saveComponent(form)
    },
    enabled: !!currentInfo.value?.id
  }, {
    labelKey: 'common.label.delete',
    type: 'danger',
    click () {
      apiComponentRef.value?.deleteComponent()?.then(deleted => {
        if (deleted) showWindow.value = false
      })
    },
    enabled: !!currentInfo.value?.id
  }, {
    labelKey: 'common.label.copy',
    type: 'warning',
    click () {
      apiComponentRef.value?.copyComponent()
    },
    enabled: !!currentInfo.value?.id
  }]
})
</script>

<template>
  <common-window
    v-model="showWindow"
    width="1100px"
    :title="$i18nKey('common.label.commonEdit', 'api.label.dataModel')"
    :ok-label="$t('common.label.saveAndClose')"
    :cancel-label="$t('common.label.close')"
    :ok-click="saveComponent"
    :buttons="buttons"
    :ok-loading="apiComponentRef?.saving"
    :close-click="closeComponent"
    :cancel-click="closeComponent"
    :destroy-on-close="true"
  >
    <api-project-component
      v-if="currentInfo && showWindow"
      ref="apiComponent"
      v-model="currentInfo"
      v-bind="$attrs"
    />
  </common-window>
</template>

<style scoped>

</style>
