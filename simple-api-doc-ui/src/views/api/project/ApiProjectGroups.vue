<script setup lang="jsx">
import { computed, onActivated, onMounted, ref } from 'vue'
import { $coreConfirm } from '@/utils'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useDefaultPage } from '@/config'
import ApiProjectGroupApi from '@/api/ApiProjectGroupApi'
import { $i18nBundle } from '@/messages'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import { defineFormOptions } from '@/components/utils'
import { useFormStatus } from '@/consts/GlobalConstants'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'

const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { keyword: '', page: useDefaultPage() },
  searchMethod: ApiProjectGroupApi.search
})
const loadProjectGroups = (pageNumber) => searchMethod(pageNumber)
const { initLoadOnce } = useInitLoadOnce(async () => loadProjectGroups())

onMounted(initLoadOnce)

onActivated(initLoadOnce)

const columns = [{
  labelKey: 'api.label.projectGroupName',
  prop: 'groupName',
  minWidth: '120px'
}, {
  labelKey: 'common.label.status',
  minWidth: '100px',
  formatter (data) {
    return <DelFlagTag v-model={data.status} clickToToggle={true}
                       onToggleValue={(status) => saveProjectGroup({ ...data, status })}/>
  },
  attrs: {
    align: 'center'
  }
}, {
  labelKey: 'common.label.description',
  property: 'description',
  minWidth: '120px'
}]

const buttons = computed(() => {
  return [{
    labelKey: 'api.label.projectGroupUsers',
    type: 'success',
    click: item => {
      console.log('=======================配置用户', item)
    }
  }, {
    labelKey: 'common.label.edit',
    type: 'primary',
    click: item => {
      newOrEdit(item.id)
    }
  }, {
    labelKey: 'common.label.delete',
    type: 'danger',
    click: item => {
      $coreConfirm($i18nBundle('common.msg.deleteConfirm'))
        .then(() => ApiProjectGroupApi.deleteById(item.id))
        .then(() => loadProjectGroups())
    }
  }]
})
//* ************搜索框**************//
const searchFormOptions = computed(() => {
  return [{
    labelKey: 'common.label.keywords',
    prop: 'keyword'
  }]
})

const showEditWindow = ref(false)
const currentModel = ref()
const newOrEdit = async (id) => {
  if (id) {
    await ApiProjectGroupApi.getById(id).then(data => {
      if (data.resultData) {
        currentModel.value = data.resultData
      }
    })
  } else {
    currentModel.value = {
      status: 1
    }
  }
  showEditWindow.value = true
}

const editFormOptions = computed(() => {
  return defineFormOptions([{
    labelKey: 'api.label.projectGroupName',
    prop: 'groupName',
    required: true
  }, useFormStatus(), {
    labelKey: 'common.label.description',
    prop: 'description',
    attrs: {
      type: 'textarea'
    }
  }])
})

const saveProjectGroup = (data) => {
  return ApiProjectGroupApi.saveOrUpdate(data)
    .then(() => loadProjectGroups())
}

</script>

<template>
  <el-container class="flex-column">
    <common-form
      inline
      :model="searchParam"
      :options="searchFormOptions"
      :submit-label="$t('common.label.search')"
      @submit-form="loadProjectGroups(1)"
    >
      <template #buttons>
        <el-button
          type="info"
          @click="newOrEdit()"
        >
          {{ $t('common.label.new') }}
        </el-button>
      </template>
    </common-form>
    <common-table
      v-model:page="searchParam.page"
      :data="tableData"
      :columns="columns"
      :buttons="buttons"
      :buttons-column-attrs="{minWidth:'200px'}"
      buttons-slot="buttons"
      :loading="loading"
      @page-size-change="loadProjectGroups()"
      @current-page-change="loadProjectGroups()"
    />
    <simple-edit-window
      v-model="currentModel"
      v-model:show-edit-window="showEditWindow"
      :form-options="editFormOptions"
      :name="$t('api.label.importData')"
      :save-current-item="saveProjectGroup"
      label-width="130px"
    />
  </el-container>
</template>

<style scoped>

</style>
