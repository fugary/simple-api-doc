<script setup>
import { $i18nBundle, $i18nKey } from '@/messages'
import { ElMessage } from 'element-plus'

const props = defineProps({
  formOptions: {
    type: Array,
    default: () => []
  },
  name: {
    type: String,
    default: ''
  },
  saveCurrentItem: {
    type: Function,
    default: () => {}
  },
  debug: {
    type: Boolean, default: false
  }
})

const showEditWindow = defineModel('showEditWindow', {
  type: Boolean,
  default: false
})

const currentItem = defineModel('modelValue', {
  type: Object,
  default: () => ({})
})

const internalSaveCurrentItem = ({ form }) => {
  form.validate(valid => {
    if (valid) {
      props.saveCurrentItem(currentItem.value).then(() => {
        ElMessage.success($i18nBundle('common.msg.saveSuccess'))
        showEditWindow.value = false
      })
    }
  })
  return false
}

</script>

<template>
  <common-window
    v-model="showEditWindow"
    :title="currentItem?.id?$i18nKey('common.label.commonEdit', name):$i18nKey('common.label.commonAdd', name)"
    :ok-click="internalSaveCurrentItem"
    append-to-body
    destroy-on-close
  >
    <el-container class="flex-column">
      <common-form
        v-if="currentItem"
        class="form-edit-width-100"
        :model="currentItem"
        :options="formOptions"
        :show-buttons="false"
        v-bind="$attrs"
      >
        <template
          v-for="(slot, slotKey) in $slots"
          :key="slotKey"
          #[slotKey]="scope"
        >
          <slot
            :name="slotKey"
            v-bind="scope"
          />
        </template>
      </common-form>
      <pre v-if="debug">{{ currentItem }}</pre>
    </el-container>
  </common-window>
</template>

<style scoped>

</style>
