<script setup>
import { computed, ref } from 'vue'

const vModel = defineModel({
  type: Object,
  default: null
})
const showWindow = ref(false)
const toEditJsonSchema = data => {
  vModel.value = data
  showWindow.value = true
}

const formOptions = computed(() => {
  return [{
    labelKey: 'common.label.name',
    prop: 'name'
  }]
})

defineExpose({
  toEditJsonSchema
})
</script>

<template>
  <common-window v-model="showWindow">
    <common-form
      :options="formOptions"
      :model="vModel"
    />
  </common-window>
</template>

<style scoped>

</style>
