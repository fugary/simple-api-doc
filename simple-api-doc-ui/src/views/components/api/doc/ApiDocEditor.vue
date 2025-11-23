<script setup>
import { computed, ref, watch } from 'vue'
import 'md-editor-v3/lib/style.css'
import MarkdownDocEditHeader from '@/views/components/api/doc/comp/MarkdownDocEditHeader.vue'
import { $coreHideLoading, $coreShowLoading, getSingleSelectOptions, getStyleGrow } from '@/utils'
import ApiDocApi from '@/api/ApiDocApi'
import { ALL_METHODS } from '@/consts/ApiConstants'
import { useFormStatus } from '@/consts/GlobalConstants'
import { $i18nBundle, $i18nKey } from '@/messages'
import { showMarkdownWindow } from '@/utils/DynamicUtils'

const currentDoc = defineModel({
  type: Object,
  default: () => ({})
})
const currentDocModel = ref()
const formOptions = computed(() => {
  return [{
    type: 'select',
    prop: 'method',
    children: getSingleSelectOptions(...ALL_METHODS.map(method => method.method)),
    labelKey: 'api.label.requestPath',
    required: true,
    style: getStyleGrow(3),
    attrs: {
      clearable: false
    }
  }, {
    prop: 'url',
    labelWidth: '20px',
    labelKey: 'api.label.requestPath',
    showLabel: false,
    required: true,
    style: getStyleGrow(7),
    change (val) {
      if (val && !val.startsWith('/')) {
        currentDocModel.value.url = `/${val.trim()}`
      }
    }
  }, {
    ...useFormStatus(),
    style: getStyleGrow(4)
  }, {
    labelKey: 'api.label.apiDocLock',
    type: 'switch',
    prop: 'locked',
    tooltip: $i18nBundle('api.msg.apiDocLocked'),
    style: getStyleGrow(6)
  }, {
    label: 'Operation ID',
    prop: 'operationId',
    required: false,
    style: getStyleGrow(10)
  }, {
    labelKey: 'common.label.description',
    prop: 'description',
    attrs: {
      type: 'textarea',
      rows: 4
    },
    tooltip: $i18nBundle('common.label.newWindowEdit'),
    tooltipIcon: 'EditPen',
    tooltipLinkAttrs: {
      type: 'primary'
    },
    tooltipFunc () {
      showMarkdownWindow({
        content: currentDocModel.value.description,
        title: $i18nKey('common.label.commonEdit', 'common.label.description')
      }, {
        'onUpdate:modelValue': value => (currentDocModel.value.description = value)
      })
    }
  }]
})
const loadCurrentDoc = (id) => {
  $coreShowLoading({ delay: 0, target: '.home-main' })
  ApiDocApi.getById(id).then(data => {
    currentDocModel.value = data.resultData
    console.log('========================currentDocModel', currentDocModel.value)
    $coreHideLoading()
  }).catch(() => $coreHideLoading())
}
watch(currentDoc, (newDoc) => {
  if (newDoc.id) {
    loadCurrentDoc(newDoc.id)
  } else {
    currentDocModel.value = {
      ...newDoc
    }
  }
}, { immediate: true })
defineEmits(['savedDoc'])
</script>
<template>
  <el-container
    v-if="currentDocModel"
    class="flex-column padding-left2 height100 padding-bottom2"
  >
    <markdown-doc-edit-header
      v-model="currentDoc"
      v-model:doc-model="currentDocModel"
      @saved-doc="$emit('savedDoc', $event)"
    />
    <common-form
      label-width="130px"
      :options="formOptions"
      :model="currentDocModel"
      :show-buttons="false"
      class-name="common-form-auto"
    />
  </el-container>
</template>

<style scoped>

</style>
