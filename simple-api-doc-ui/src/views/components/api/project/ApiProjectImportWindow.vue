<script setup>
import { ref } from 'vue'
import ApiProjectImport from '@/views/components/api/project/ApiProjectImport.vue'

const props = defineProps({
  project: {
    type: Object,
    default: null
  },
  autoAlert: {
    type: Boolean,
    default: true
  }
})
const showWindow = defineModel('modelValue', { type: Boolean, default: false })
const importRef = ref()
const doImportProject = () => {
  importRef.value?.doImportProject(props.autoAlert)
  return false
}
defineEmits(['import-success'])
</script>

<template>
  <common-window
    v-model="showWindow"
    :title="$t('api.msg.importFileTitle')"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    width="800px"
    :ok-click="doImportProject"
    :ok-label="$t('api.label.importData')"
  >
    <api-project-import
      ref="importRef"
      :project="project"
      @import-success="$emit('import-success', $event)"
    />
  </common-window>
</template>

<style scoped>

</style>
