import { computed, ref } from 'vue'
import { defineFormOptions } from '@/components/utils'
import { useFormStatus } from '@/consts/GlobalConstants'
import { isAdminUser, useCurrentUserName } from '@/utils'
import ApiProjectGroupApi from '@/api/ApiProjectGroupApi'

export const useProjectGroupEditHook = (formModel, userOptions) => {
  const showEditWindow = ref(false)
  const currentGroup = ref()
  const newOrEditGroup = async (id, $event) => {
    $event?.stopPropagation()
    if (id) {
      await ApiProjectGroupApi.getById(id).then(data => {
        data.resultData && (currentGroup.value = data.resultData)
      })
    } else {
      currentGroup.value = {
        status: 1,
        userName: formModel.value?.userName || useCurrentUserName()
      }
    }
    showEditWindow.value = true
  }
  const editFormOptions = computed(() => defineFormOptions([{
    labelKey: 'common.label.user',
    prop: 'userName',
    type: 'select',
    enabled: isAdminUser(),
    children: userOptions.value,
    attrs: {
      clearable: false
    }
  }, {
    labelKey: 'api.label.projectGroupName',
    prop: 'groupName',
    required: true
  }, useFormStatus(), {
    labelKey: 'common.label.description',
    prop: 'description',
    attrs: {
      type: 'textarea'
    }
  }]))
  return {
    currentGroup,
    newOrEditGroup,
    showEditWindow,
    editFormOptions
  }
}
