<script setup lang="jsx">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { $copyText, $coreConfirm, $openNewWin, formatDate, useBackUrl } from '@/utils'
import { useApiProjectItem } from '@/api/ApiProjectApi'
import { useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useDefaultPage } from '@/config'
import ApiProjectShareApi, { getShareUrl } from '@/api/ApiProjectShareApi'
import { $i18nBundle } from '@/messages'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import { defineFormOptions } from '@/components/utils'
import { useFormStatus } from '@/consts/GlobalConstants'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import UrlCopyLink from '@/views/components/api/UrlCopyLink.vue'
import { ElTag } from 'element-plus'

const route = useRoute()
const projectCode = route.params.projectCode

const { goBack } = useBackUrl(`/api/projects/${projectCode}`)
const { projectItem, loadProjectItem } = useApiProjectItem(projectCode, false)

const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { keyword: '', page: useDefaultPage() },
  searchMethod: ApiProjectShareApi.search
})
const loadProjectShares = (pageNumber) => searchMethod(pageNumber)

onMounted(async () => {
  await loadProjectItem(projectCode)
  searchParam.value.projectId = projectItem.value?.id
  if (searchParam.value.projectId) {
    loadProjectShares(1)
  }
})

const columns = [{
  labelKey: 'api.label.shareName',
  formatter (data) {
    let shareUrl = getShareUrl(data.shareId)
    if (data.sharePassword) {
      shareUrl = `${shareUrl}?pwd=${data.sharePassword}`
    }
    return <>
      {data.shareName}&nbsp;
      <UrlCopyLink urlPath={shareUrl} />&nbsp;
      <UrlCopyLink icon="OpenInNewFilled"
                   tooltip={$i18nBundle('api.label.openLink')}
                   onClick={() => $openNewWin(shareUrl)} />
      </>
  }
}, {
  labelKey: 'common.label.status',
  minWidth: '100px',
  formatter (data) {
    return <DelFlagTag v-model={data.status} clickToToggle={true}
                       onToggleValue={(status) => saveProjectShare({ ...data, status })}/>
  },
  attrs: {
    align: 'center'
  }
}, {
  labelKey: 'api.label.hasPassword',
  minWidth: '100px',
  formatter (data) {
    const type = data.sharePassword ? 'success' : 'danger'
    const text = data.sharePassword ? $i18nBundle('common.label.yes') : $i18nBundle('common.label.no')
    return <ElTag type={type}>
      {text}
    </ElTag>
  },
  attrs: {
    align: 'center'
  }
}, {
  labelKey: 'common.label.createDate',
  property: 'createDate',
  dateFormat: 'YYYY-MM-DD HH:mm:ss'
}, {
  labelKey: 'api.label.expireDate',
  formatter: (row) => {
    if (row.expireDate) {
      return formatDate(row.expireDate)
    }
    return $i18nBundle('api.label.noExpires')
  }
}]

const buttons = computed(() => {
  return [{
    labelKey: 'api.label.copyLinkAndPassword',
    type: 'success',
    click: item => {
      const shareUrl = getShareUrl(item.shareId)
      let text = `${item.shareName}: \n链接: ${shareUrl}`
      if (item.sharePassword) {
        text += `\n密码: ${item.sharePassword}`
      }
      $copyText(text)
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
        .then(() => ApiProjectShareApi.deleteById(item.id))
        .then(() => loadProjectShares())
    }
  }]
})
//* ************搜索框**************//
const searchFormOptions = computed(() => {
  return [
    {
      labelKey: 'common.label.keywords',
      prop: 'keyword'
    }
  ]
})

const showEditWindow = ref(false)
const currentShare = ref()
const newOrEdit = async (id) => {
  if (id) {
    await ApiProjectShareApi.getById(id).then(data => {
      data.resultData && (currentShare.value = data.resultData)
    })
  } else {
    currentShare.value = {
      projectId: projectItem.value.id,
      status: 1,
      exportEnabled: true,
      debugEnabled: true,
      defaultTheme: 'dark',
      defaultShowLabel: 'docName'
    }
  }
  showEditWindow.value = true
}
const editFormOptions = computed(() => defineFormOptions([{
  labelKey: 'api.label.shareName',
  prop: 'shareName',
  required: true
}, useFormStatus(), {
  labelKey: 'api.label.exportEnabled',
  prop: 'exportEnabled',
  type: 'switch'
}, {
  labelKey: 'api.label.debugEnabled',
  prop: 'debugEnabled',
  type: 'switch'
}, {
  labelKey: 'common.label.defaultTheme',
  prop: 'defaultTheme',
  type: 'select',
  children: [{
    value: 'dark',
    labelKey: 'Dark'
  }, {
    value: 'light',
    labelKey: 'Light'
  }]
}, {
  labelKey: 'common.label.defaultShowLabel',
  prop: 'defaultShowLabel',
  children: [{
    value: 'docName',
    labelKey: 'api.label.docLabelShowName'
  }, {
    value: 'url',
    labelKey: 'api.label.docLabelShowUrl'
  }]
}, {
  labelKey: 'api.label.accessPassword',
  prop: 'sharePassword',
  attrs: {
    showPassword: true
  }
}, {
  labelKey: 'api.label.expireDate',
  prop: 'expireDate',
  type: 'date-picker'
}]))

const saveProjectShare = (item) => {
  return ApiProjectShareApi.saveOrUpdate(item).then(() => loadProjectShares())
}

</script>

<template>
  <el-container class="flex-column">
    <el-page-header
      class="margin-bottom3"
      @back="goBack"
    >
      <template #content>
        <el-container>
          <span>
            {{ projectItem?.projectName }} - {{ $t('api.label.shareDocs') }}
          </span>
        </el-container>
      </template>
    </el-page-header>
    <common-form
      inline
      :model="searchParam"
      :options="searchFormOptions"
      :submit-label="$t('common.label.search')"
      :back-url="goBack"
      @submit-form="loadProjectShares(1)"
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
      buttons-slot="buttons"
      :buttons-column-attrs="{minWidth:'250px'}"
      :loading="loading"
      @page-size-change="loadProjectShares()"
      @current-page-change="loadProjectShares()"
    />
    <simple-edit-window
      v-model="currentShare"
      v-model:show-edit-window="showEditWindow"
      :form-options="editFormOptions"
      :name="$t('api.label.shareDocs')"
      :save-current-item="saveProjectShare"
      label-width="130px"
    />
  </el-container>
</template>

<style scoped>

</style>
