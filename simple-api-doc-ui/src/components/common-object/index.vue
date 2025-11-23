<script setup>
import { ref, watch } from 'vue'
import CommonParamsEdit from '@/views/components/utils/CommonParamsEdit.vue'
import { isEqual } from 'lodash-es'

defineProps({
  readonly: {
    type: Boolean,
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const vModel = defineModel({
  type: Object,
  default: () => ({})
})

const parsedModel = ref({
  params: []
})

watch(vModel, value => {
  console.log('==============value', value)
  parsedModel.value.params = Object.entries(value).map(([k, v]) => {
    return {
      name: k,
      value: v
    }
  })
}, { immediate: true })

const parseModelParams = params => {
  const newVal = params.filter(param => !!param.name).reduce((res, item) => {
    res[item.name] = item.value
    return res
  }, {})
  if (!isEqual(newVal, vModel.value)) {
    console.log('==============update', vModel)
    vModel.value = newVal
  }
}

watch(() => parsedModel.value.params, parseModelParams, { deep: true })

</script>

<template>
  <el-container class="flex-column">
    <common-form
      :model="parsedModel"
      :show-buttons="false"
    >
      <common-params-edit
        v-model="parsedModel.params"
        class="form-edit-width-100"
        form-prop="params"
        name-required
        :name-read-only="readonly||disabled"
        :value-read-only="readonly||disabled"
        :show-enable-switch="false"
        :show-copy-button="false"
        :show-add-button="!disabled&&!readonly"
        :show-paste-button="!disabled&&!readonly"
        :show-remove-button="!disabled&&!readonly"
      />
    </common-form>
  </el-container>
</template>

<style scoped>

</style>
