<script setup>
import { computed, inject } from 'vue'
import DocHeaderBreadcrumb from './DocHeaderBreadcrumb.vue'
import ApiDocApi from '@/api/ApiDocApi'
import { ElMessage } from 'element-plus'
import { $i18nBundle, $i18nKey } from '@/messages'
import DocDraftNotice from '@/views/components/api/doc/comp/DocDraftNotice.vue'

const currentDoc = defineModel({ // 原始数据
  type: Object,
  default: undefined
})

const currentDocModel = defineModel('docModel', { // 编辑数据，独立数据取消编辑才可以还原
  type: Object,
  default: undefined
})

const docFormOption = computed(() => {
  return {
    showLabel: currentDoc.value?.docType === 'api',
    labelKey: 'api.label.requestName',
    type: 'input',
    prop: 'docName',
    placeholder: $i18nKey('common.msg.commonInput', 'api.label.docName'),
    required: true,
    style: {
      flexGrow: 5
    },
    attrs: {
      size: 'large'
    },
    change (value) {
      currentDocModel.value.label = value
    }
  }
})

const emit = defineEmits(['savedDoc'])
const saving = defineModel('saving', { type: Boolean, default: false })
const props = defineProps({
  projectItem: { type: Object, default: undefined },
  beforeCancel: { type: Function, default: () => true },
  draft: { type: Object, default: undefined }
})
const cancelEdit = async () => {
  if (!saving.value && await props.beforeCancel()) currentDoc.value.editing = false
}

const saveApiDoc = async (form) => {
  if (saving.value) return
  saving.value = true
  try {
    await form.validate()
    const data = await ApiDocApi.saveOrUpdate({ ...currentDocModel.value, parent: undefined }, { loading: true })
    if (data.success) {
      ElMessage.success($i18nBundle('common.msg.saveSuccess'))
      emit('savedDoc', data.resultData)
    }
  } catch {
    // 表单和请求层负责错误提示，失败时保留编辑内容及草稿。
  } finally {
    saving.value = false
  }
}
const showAffixBtn = inject('showAffixBtn', null)
</script>

<template>
  <el-header
    style="min-height: var(--el-header-height);height:auto;"
    :style="showAffixBtn?'padding-left: 50px;':''"
  >
    <doc-header-breadcrumb
      :doc="currentDoc"
      :folders="projectItem?.folders"
      class="margin-bottom3"
    />
    <common-form
      inline
      class-name="common-form-auto"
      :model="currentDocModel"
      :show-buttons="false"
      label-width="130px"
    >
      <template #default="{form}">
        <common-form-control
          :model="currentDocModel"
          prop="docName"
          :option="docFormOption"
        />
        <el-form-item style="flex-grow: 2;padding-top: 3px">
          <el-button
            type="primary"
            :loading="saving"
            @click="saveApiDoc(form)"
          >
            {{ $t('common.label.save') }}
          </el-button>
          <el-button
            type="info"
            :disabled="saving"
            @click="cancelEdit"
          >
            {{ $t('common.label.cancel') }}
          </el-button>
          <doc-draft-notice
            v-if="draft"
            :draft="draft"
          />
        </el-form-item>
      </template>
    </common-form>
  </el-header>
</template>
