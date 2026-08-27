<script setup>
import { ref } from 'vue'
import { $i18nKey } from '@/messages'
import ApiFolderApi from '@/api/ApiFolderApi'
import { getChildrenSortId } from '@/services/api/ApiFolderService'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'

const currentEditFolder = ref()
const showEditWindow = ref(false)
const editFormOptions = [{
  labelKey: 'api.label.folderName',
  prop: 'folderName',
  placeholder: $i18nKey('common.msg.commonInput', 'api.label.folderName'),
  required: true
}, {
  labelKey: 'api.label.folderCode',
  prop: 'folderCode',
  placeholder: $i18nKey('common.msg.commonInput', 'api.label.folderCode'),
  attrs: {
    clearable: true
  }
}]
const addOrEditFolderWindow = async (id, projectId, parentFolder) => {
  if (id) {
    await ApiFolderApi.getById(id).then(data => {
      data.resultData && (currentEditFolder.value = data.resultData)
    })
  } else {
    currentEditFolder.value = {
      status: 1,
      projectId,
      parentId: parentFolder?.id,
      sortId: getChildrenSortId(parentFolder)
    }
  }
  showEditWindow.value = true
}
const emit = defineEmits(['savedFolder'])
const saveFolder = () => {
  const folderData = { ...currentEditFolder.value, children: undefined }
  if (!folderData.folderCode) {
    folderData.folderCode = folderData.folderName
  }
  return ApiFolderApi.saveOrUpdate(folderData, { loading: true }).then(data => {
    if (data.success) {
      emit('savedFolder', data.resultData)
      showEditWindow.value = false
    }
  })
}
defineExpose({
  addOrEditFolderWindow
})

</script>

<template>
  <simple-edit-window
    v-model="currentEditFolder"
    v-model:show-edit-window="showEditWindow"
    width="500px"
    :form-options="editFormOptions"
    :name="$t('api.label.folder')"
    :save-current-item="saveFolder"
    label-width="130px"
  />
</template>

<style scoped>

</style>
