<script setup lang="jsx">
import { ref, useTemplateRef } from 'vue'
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
  apiComponentRef.value.saveComponent(form)
}
defineExpose({
  toEditComponent
})
</script>

<template>
  <common-window
    v-model="showWindow"
    width="1100px"
    :title="$i18nKey('common.label.commonEdit', 'api.label.dataModel')"
    :ok-label="$t('common.label.save')"
    :ok-click="saveComponent"
  >
    <api-project-component
      v-if="currentInfo"
      ref="apiComponent"
      v-model="currentInfo"
      v-bind="$attrs"
    />
  </common-window>
</template>

<style scoped>

</style>
