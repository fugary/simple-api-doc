<script setup lang="jsx">
import { computed, onMounted, ref, onActivated } from 'vue'
import { useRoute } from 'vue-router'
import { $copyText, $coreConfirm, $openNewWin, $randomStr, formatDate, useBackUrl, isAdminUser } from '@/utils'
import { loadBasicById, useApiProjectItem, useSelectProjects } from '@/api/ApiProjectApi'
import { useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useDefaultPage } from '@/config'
import ApiProjectShareApi, { getShareUrl } from '@/api/ApiProjectShareApi'
import { $i18nBundle } from '@/messages'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import { defineFormOptions } from '@/components/utils'
import { useFormStatus } from '@/consts/GlobalConstants'
import DelFlagTag from '@/views/components/utils/DelFlagTag.vue'
import UrlCopyLink from '@/views/components/api/UrlCopyLink.vue'
import { ElTag, ElButton } from 'element-plus'
import { useAllUsers } from '@/api/ApiUserApi'

const route = useRoute()
const projectCode = route.params.projectCode
const inProject = !!projectCode

const { goBack } = useBackUrl(`/api/projects/${projectCode}`)
const { projectItem, loadProjectItem } = useApiProjectItem(projectCode, { autoLoad: false, detail: false })

const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { keyword: '', page: useDefaultPage() },
  searchMethod: ApiProjectShareApi.search
})
const loadProjectShares = (pageNumber) => searchMethod(pageNumber)

const { userOptions, loadUsersAndRefreshOptions } = useAllUsers(searchParam)
const { projectOptions, loadProjectsAndRefreshOptions } = useSelectProjects(searchParam)
const infoList = ref([])

onMounted(async () => {
  if (inProject) {
    await loadProjectItem(projectCode)
    searchParam.value.projectId = projectItem.value?.id
    searchParam.value.userName = projectItem.value?.userName
    infoList.value = projectItem.value?.infoList
  } else {
    loadUsersAndRefreshOptions()
    loadProjectsAndRefreshOptions()
  }
  loadProjectShares(1)
})

onActivated(async () => {
  if (!inProject) {
    await Promise.allSettled([loadUsersAndRefreshOptions(), loadProjectsAndRefreshOptions()])
  }
  loadProjectShares()
})

const columns = [{
  labelKey: 'api.label.shareName',
  minWidth: '120px',
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
  labelKey: 'api.label.project',
  prop: 'project.projectName',
  minWidth: '120px'
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
  dateFormat: 'YYYY-MM-DD HH:mm:ss',
  minWidth: '120px'
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
      labelKey: 'common.label.user',
      prop: 'userName',
      type: 'select',
      enabled: !inProject && isAdminUser(),
      children: userOptions.value,
      attrs: {
        clearable: false
      },
      change: async () => {
        await loadProjectsAndRefreshOptions()
        loadProjectShares(1)
      }
    }, {
      labelKey: 'api.label.project',
      prop: 'projectId',
      type: 'select',
      enabled: !inProject && projectOptions.value.length > 1,
      children: projectOptions.value,
      change () {
        loadProjectShares(1)
      }
    },
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
      if (currentShare.value.envContent) {
        currentShare.value.shareEnvs = JSON.parse(currentShare.value.envContent)
          .map(env => env.url)
      }
      if (!inProject) {
        loadBasicById(currentShare.value.projectId).then(data => {
          infoList.value = data?.infoList
        })
      }
    })
  } else {
    currentShare.value = {
      projectId: projectItem.value?.id,
      status: 1,
      exportEnabled: true,
      debugEnabled: true,
      defaultTheme: 'dark',
      defaultShowLabel: 'docName'
    }
    if (!inProject) {
      infoList.value = []
    }
  }
  showEditWindow.value = true
}

const envConfigs = computed(() => {
  if (infoList.value?.length) {
    return infoList.value.flatMap(info => {
      let envs = []
      if (info.envContent && (envs = JSON.parse(info.envContent))?.length) {
        return envs
      }
      return []
    })
  }
  return []
})

const editFormOptions = computed(() => {
  const envOptions = envConfigs.value.map(env => {
    return {
      value: env.url,
      label: env.name || $i18nBundle('api.label.defaultAddress')
    }
  })
  return defineFormOptions([{
    labelKey: 'api.label.shareName',
    prop: 'shareName',
    required: true
  }, {
    labelKey: 'api.label.project',
    prop: 'projectId',
    required: true,
    type: 'select',
    enabled: !inProject,
    disabled: !!currentShare.value?.id,
    children: projectOptions.value,
    change (projectId) {
      loadBasicById(projectId).then(data => {
        infoList.value = data?.infoList
      })
      currentShare.value.shareEnvs = []
    }
  }, useFormStatus(), {
    labelKey: 'api.label.exportEnabled',
    prop: 'exportEnabled',
    type: 'switch'
  }, {
    labelKey: 'api.label.debugEnabled',
    prop: 'debugEnabled',
    type: 'switch'
  }, {
    labelKey: 'api.label.accessPassword',
    prop: 'sharePassword',
    slots: {
      append () {
        const generatePass = () => {
          currentShare.value.sharePassword = $randomStr(8)
        }
        return <ElButton type="primary" onClick={generatePass}>
          {$i18nBundle('common.label.generate')}
        </ElButton>
      }
    }
  }, {
    labelKey: 'api.label.expireDate',
    prop: 'expireDate',
    type: 'date-picker'
  }, {
    labelKey: 'api.label.environments',
    prop: 'shareEnvs',
    type: 'select',
    attrs: {
      clearable: false,
      multiple: true
    },
    children: envOptions
  }, {
    labelKey: 'common.label.defaultTheme',
    prop: 'defaultTheme',
    type: 'select',
    attrs: {
      clearable: false
    },
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
    type: 'select',
    attrs: {
      clearable: false
    },
    children: [{
      value: 'docName',
      labelKey: 'api.label.docLabelShowName'
    }, {
      value: 'url',
      labelKey: 'api.label.docLabelShowUrl'
    }]
  }])
})

const saveProjectShare = (item) => {
  item = { ...item, envContent: undefined }
  if (item.shareEnvs?.length) {
    item.envContent = JSON.stringify(envConfigs.value.filter(env => item.shareEnvs.includes(env.url)))
  }
  return ApiProjectShareApi.saveOrUpdate(item).then(() => loadProjectShares())
}

</script>

<template>
  <el-container class="flex-column">
    <el-page-header
      v-if="inProject"
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
      :back-url="inProject?goBack:''"
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
