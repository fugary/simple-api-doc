<script setup>
import { loadClientLanguages, loadLanguageConfig } from '@/api/SwaggerGeneratorApi'
import { computed, ref, watch } from 'vue'
import { defineFormOptions } from '@/components/utils'
import { getSingleSelectOptions } from '@/utils'

const props = defineProps({
  spec: {
    type: Object,
    default: undefined
  },
  generatorParam: {
    type: Object,
    default: null
  }
})

const vModel = defineModel('modelValue', {
  type: Object,
  required: true
})

const loading = ref(false)
const languages = ref([])
loadClientLanguages(props.generatorParam).then(data => {
  languages.value = data
})

const languageConfig = ref({})

watch(() => vModel.value?._language, (language) => {
  if (language) {
    loading.value = true
    loadLanguageConfig(language, props.generatorParam, { loading: true }).then(data => {
      languageConfig.value = data
      const { _language } = vModel.value
      vModel.value = { _language }
      console.log('========================config', languageConfig.value)
    }).finally(() => {
      loading.value = false
    })
  }
}, { immediate: true })

const options = computed(() => {
  console.log('==========================options', languageConfig.value)
  const languageOptions = Object.keys(languageConfig.value).map(key => {
    const config = languageConfig.value[key]
    const tooltip = config.description
    const option = {
      prop: key,
      label: key,
      value: config.default,
      tooltip
    }
    option.type = 'input'
    if (config.type === 'boolean') {
      option.type = 'switch'
      option.value = config.default === 'true'
    } else if (config.enum) {
      option.type = 'select'
      option.children = Object.keys(config.enum)
        .map(key => ({ value: key, label: key + ' - ' + config.enum[key] }))
      option.attrs = {
        filterable: true,
        fitInputWidth: true
      }
    }
    return option
  })
  return defineFormOptions([{
    labelKey: 'common.label.language',
    type: 'select',
    prop: '_language',
    children: getSingleSelectOptions(...languages.value),
    attrs: {
      filterable: true,
      clearable: false
    }
  }, ...languageOptions])
})

</script>

<template>
  <common-form
    v-loading="loading"
    label-width="250px"
    :model="vModel"
    :options="options"
    :show-buttons="false"
    style="min-height: 200px;width: 95%;"
  />
</template>

<style scoped>

</style>
