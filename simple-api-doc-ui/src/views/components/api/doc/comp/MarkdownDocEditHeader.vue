<script setup>
import { computed } from 'vue'
import { getFolderPaths } from '@/services/api/ApiProjectService'
import ApiDocApi from '@/api/ApiDocApi'
import { ElMessage } from 'element-plus'
import { $i18nBundle, $i18nKey } from '@/messages'

const currentDoc = defineModel({ // 原始数据
  type: Object,
  default: undefined
})

const currentDocModel = defineModel('docModel', { // 编辑数据，独立数据取消编辑才可以还原
  type: Object,
  default: undefined
})

const folderPaths = computed(() => {
  if (currentDoc.value) {
    return getFolderPaths(currentDoc.value)
  }
  return []
})

const docFormOption = computed(() => {
  return {
    showLabel: false,
    type: 'input',
    prop: 'docName',
    placeholder: $i18nKey('common.msg.commonInput', 'api.label.docName'),
    required: true,
    attrs: {
      style: {
        minWidth: '50vw'
      },
      size: 'large'
    },
    change (value) {
      currentDocModel.value.label = value
    }
  }
})

const emit = defineEmits(['savedDoc'])

const saveApiDoc = (form) => {
  form.validate(valid => {
    if (valid) {
      ApiDocApi.saveOrUpdate({ ...currentDocModel.value, parent: undefined }, { loading: true }).then(data => {
        if (data.success) {
          ElMessage.success($i18nBundle('common.msg.saveSuccess'))
          emit('savedDoc', data.resultData)
        }
      })
    }
  })
}
</script>

<template>
  <el-header style="min-height: var(--el-header-height);height:auto;">
    <el-breadcrumb
      v-if="folderPaths.length>1"
      class="margin-bottom3"
    >
      <el-breadcrumb-item
        v-for="(folderPath, index) in folderPaths"
        :key="index"
      >
        {{ folderPath }}
      </el-breadcrumb-item>
    </el-breadcrumb>
    <common-form
      inline
      :model="currentDocModel"
      :submit-label="$t('common.label.save')"
      @submit-form="saveApiDoc"
    >
      <common-form-control
        :model="currentDocModel"
        prop="docName"
        :option="docFormOption"
      />
      <template #buttons>
        <el-button
          type="info"
          @click="currentDoc.editing=false"
        >
          {{ $t('common.label.cancel') }}
        </el-button>
      </template>
    </common-form>
  </el-header>
</template>

<style scoped>

</style>
