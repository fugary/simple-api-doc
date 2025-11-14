<script setup>
import { computed, ref, watch } from 'vue'
import 'md-editor-v3/lib/style.css'
import MarkdownDocEditHeader from '@/views/components/api/doc/comp/MarkdownDocEditHeader.vue'
import { $coreHideLoading, $coreShowLoading, getSingleSelectOptions } from '@/utils'
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
const urlOptions = computed(() => {
  return [{
    type: 'select',
    prop: 'method',
    children: getSingleSelectOptions(...ALL_METHODS.map(method => method.method)),
    labelKey: 'api.label.requestPath',
    required: true,
    style: {
      flexGrow: 2
    },
    attrs: {
      clearable: false
    }
  }, {
    prop: 'url',
    labelWidth: '20px',
    labelKey: 'api.label.requestPath',
    showLabel: false,
    required: true,
    style: {
      flexGrow: 5
    }
  }]
})
const formOptions = computed(() => {
  return [useFormStatus(), {
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
      :options="formOptions"
      :model="currentDocModel"
      :show-buttons="false"
    >
      <template #before-options>
        <div
          class="common-form-auto"
        >
          <common-form-control
            v-for="(option, optIdx) in urlOptions"
            :key="optIdx"
            :model="currentDocModel"
            :option="option"
            :prop="option.prop"
          />
        </div>
      </template>
    </common-form>
  </el-container>
</template>

<style scoped>

</style>
