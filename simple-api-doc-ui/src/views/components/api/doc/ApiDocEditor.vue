<script setup>
import { computed } from 'vue'
import { useDocDraft } from '@/hooks/useDocDraft'
import DocEditorLayout from '@/views/components/api/doc/comp/DocEditorLayout.vue'
import 'md-editor-v3/lib/style.css'
import { getSingleSelectOptions, getStyleGrow } from '@/utils'
import { ALL_METHODS } from '@/consts/ApiConstants'
import { useFormStatus } from '@/consts/GlobalConstants'
import { $i18nBundle, $i18nKey } from '@/messages'
import { showMarkdownWindow } from '@/utils/DynamicUtils'

defineProps({ projectItem: { type: Object, default: undefined } })
const currentDoc = defineModel({
  type: Object,
  default: () => ({})
})
const emit = defineEmits(['savedDoc'])
const { model: currentDocModel, saving, draft, onSaved } = useDocDraft(currentDoc, emit)

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
    style: getStyleGrow(3)
  }, {
    labelKey: 'api.label.deprecated',
    type: 'switch',
    prop: 'deprecated',
    tooltip: $i18nBundle('api.msg.apiDocDeprecated'),
    style: getStyleGrow(3)
  }, {
    label: 'Operation ID',
    prop: 'operationId',
    placeholder: $i18nBundle('api.msg.operationIdPlaceholder'),
    tooltip: $i18nBundle('api.msg.operationIdTooltip'),
    required: false,
    attrs: {
      clearable: true
    },
    style: getStyleGrow(10)
  }, {
    labelKey: 'common.label.description',
    prop: 'docContent',
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
        content: currentDocModel.value.docContent,
        title: $i18nKey('common.label.commonEdit', 'common.label.description')
      }, {
        'onUpdate:modelValue': value => (currentDocModel.value.docContent = value)
      })
    }
  }]
})

</script>
<template>
  <doc-editor-layout
    v-model="currentDoc"
    v-model:doc-model="currentDocModel"
    v-model:saving="saving"
    :draft="draft"
    :project-item="projectItem"
    @saved-doc="onSaved"
  >
    <common-form
      label-width="130px"
      :options="formOptions"
      :model="currentDocModel"
      :show-buttons="false"
      class-name="common-form-auto"
    />
  </doc-editor-layout>
</template>
