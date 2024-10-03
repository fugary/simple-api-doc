<script setup>
import { ref } from 'vue'
import ApiProjectImport from '@/views/components/api/project/ApiProjectImport.vue'

defineProps({
  project: {
    type: Object,
    default: null
  }
})
const showWindow = defineModel('modelValue', { type: Boolean, default: false })
const importRef = ref()
const doImportProject = () => {
  importRef.value?.doImportProject()
}
defineEmits(['import-success'])
</script>

<template>
  <common-window
    v-model="showWindow"
    :title="$t('api.msg.importFileTitle')"
    append-to-body
    destroy-on-close
    width="800px"
    :ok-click="doImportProject"
  >
    <api-project-import
      ref="importRef"
      :project="project"
      @import-success="$emit('import-success')"
    />
  </common-window>
</template>

<style scoped>

</style>
