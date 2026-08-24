<script setup lang="jsx">
import { computed, nextTick, onActivated, onMounted, reactive, ref, useTemplateRef } from 'vue'
import { useRoute } from 'vue-router'
import { useBackUrl, $coreConfirm } from '@/utils'
import { useApiProjectItem } from '@/api/ApiProjectApi'
import { useInitLoadOnce, useTableAndSearchForm } from '@/hooks/CommonHooks'
import { useDefaultPage } from '@/config'
import ApiProjectInfoDetailApi, { copyApiModel, removeByQuery } from '@/api/ApiProjectInfoDetailApi'
import { inProjectCheckAccess } from '@/api/ApiProjectGroupApi'
import { AUTHORITY_TYPE } from '@/consts/ApiConstants'
import ApiProjectComponent from '@/views/components/api/project/ApiProjectComponent.vue'
import { processProjectInfos } from '@/services/api/ApiDocEditService'
import { $i18nBundle } from '@/messages'
import { ElMessage } from 'element-plus'
import CommonIcon from '@/components/common-icon/index.vue'

const route = useRoute()
const projectCode = route.params.projectCode
const componentsTableRef = useTemplateRef('componentsTableRef')

const { goBack } = useBackUrl(`/api/projects/${projectCode}`)
const { projectItem, loadProjectItem } = useApiProjectItem(projectCode, { autoLoad: false, detail: false })
const currentInfoDetail = ref(null)

const { tableData, loading, searchParam, searchMethod } = useTableAndSearchForm({
  defaultParam: { keyword: '', page: useDefaultPage(15), bodyType: 'component' },
  searchMethod: ApiProjectInfoDetailApi.search
})
const loadProjectComponents = (pageNumber) => searchMethod(pageNumber).then(data => {
  if (data.resultData?.length) {
    if (!currentInfoDetail.value) {
      currentInfoDetail.value = data.resultData[0]
    } else {
      console.log('=========================', data.resultData, currentInfoDetail.value)
      currentInfoDetail.value = data.resultData.find(item => item.id === currentInfoDetail.value.id) || data.resultData[0]
    }
    componentsTableRef.value?.table?.setCurrentRow(currentInfoDetail.value)
  } else {
    currentInfoDetail.value = null
  }
  return data
})

const newOrEdit = (id) => {
  if (id) {
    currentInfoDetail.value = { id }
  } else {
    console.log('=========================new', id)
    currentInfoDetail.value = {
      bodyType: 'component',
      schemaContent: '{"type":"object","properties":{}}',
      projectId: projectItem.value?.id,
      infoId: searchParam.value?.infoId || projectItem.value?.infoList?.[0]?.id
    }
  }
}

const confirmRemoveAllByQuery = async () => {
  const stat = await removeByQuery(Object.assign({}, searchParam.value, { checkOnly: true }), { loading: true }).then(res => res?.resultData)
  if (!stat || !stat.totalCount) {
    ElMessage.warning($i18nBundle('api.msg.noComponentsToDelete'))
    return
  }
  let msg = $i18nBundle('api.msg.clearComponentsConfirm', [stat.totalCount])
  if (stat.lockedCount > 0) {
    msg += '<br/>' + $i18nBundle('api.msg.clearComponentsLockedNotice', [stat.lockedCount])
  }
  msg += '<br/><span class="text-danger margin-top2 inline-block">' + $i18nBundle('common.msg.deleteConfirm') + '</span>'

  await $coreConfirm(msg, $i18nBundle('common.label.reminder'))
  await removeByQuery(Object.assign({}, searchParam.value, { checkOnly: false }), { loading: true })
  ElMessage.success($i18nBundle('common.msg.deleteSuccess'))
  currentInfoDetail.value = null
  loadProjectComponents(1)
}

const { initLoadOnce } = useInitLoadOnce(async () => {
  await loadProjectItem(projectCode)
  searchParam.value.projectId = projectItem.value?.id
  await loadProjectComponents()
})

onMounted(initLoadOnce)

onActivated(initLoadOnce)
const projectInfos = computed(() => {
  if (projectItem.value?.infoList?.length) {
    return processProjectInfos(projectItem.value)
  }
  return []
})
const searchFormOptions = computed(() => {
  const infoOptions = projectInfos.value?.map(info => ({
    label: info.folderName,
    value: info.id
  }))
  return [{
    labelKey: 'common.label.keywords',
    prop: 'keyword',
    attrs: {
      onChange () {
        loadProjectComponents()
      }
    }
  }, {
    labelKey: 'api.label.lockStatus',
    prop: 'locked',
    type: 'select',
    children: [{
      labelKey: 'api.label.locked',
      value: true
    }, {
      labelKey: 'api.label.unlocked',
      value: false
    }],
    change () {
      loadProjectComponents()
    }
  }, {
    labelKey: 'api.label.importFolder',
    enabled: infoOptions?.length > 1,
    prop: 'infoId',
    type: 'select',
    children: infoOptions,
    change () {
      loadProjectComponents()
    }
  }]
})
const columns = [{
  headerSlot: 'buttonHeader',
  labelKey: 'api.label.dataModel',
  prop: 'schemaName',
  formatter (data) {
    let lockIcon = null
    if (data.locked) {
      lockIcon = <CommonIcon
        icon="LockFilled"
        size={16}
        class="margin-right1"
        style="flex-shrink: 0;"
        v-common-tooltip={$i18nBundle('api.msg.apiDocLocked')}
      />
    }
    return <span class="flex-center-col">
      {lockIcon}
      <span class="ellipsis" title={data.schemaName}>{data.schemaName}</span>
    </span>
  }
}]

const pageAttrs = {
  layout: 'prev, pager, next',
  background: true,
  hideOnSinglePage: true,
  pagerCount: 5
}

const splitSizes = ref([25, 75])
const defaultMinSizes = ref([200, 500])

const isDeletable = computed(() => inProjectCheckAccess(projectItem.value, AUTHORITY_TYPE.DELETABLE))
const isWritable = computed(() => inProjectCheckAccess(projectItem.value, AUTHORITY_TYPE.WRITABLE) || isDeletable.value)

const saveComponent = (data) => {
  console.log('===========================data', data)
  currentInfoDetail.value = data
  loadProjectComponents()
}

const showContextMenu = ref(false)
const contextMenuPos = reactive({ x: 0, y: 0 })
const contextMenuHandlers = ref([])
const contextMenuDropdownRef = ref()

const handleContextMenuVisibleChange = (visible) => {
  if (!visible) {
    showContextMenu.value = false
  }
}

const handleContextItemClick = (handler) => {
  showContextMenu.value = false
  handler.handler?.()
}

const getRowHandlers = (row) => {
  const modelLabel = $i18nBundle('api.label.dataModel')
  const lockLabel = row.locked ? $i18nBundle('api.label.apiModelUnlock') : $i18nBundle('api.label.apiModelLock')
  return [{
    icon: 'Edit',
    iconColor: 'var(--el-color-primary)',
    label: $i18nBundle('common.label.commonEdit', [modelLabel]),
    handler: () => {
      newOrEdit(row.id)
    }
  }, {
    enabled: isWritable.value,
    icon: 'DocumentCopy',
    iconColor: 'var(--el-color-primary)',
    label: $i18nBundle('common.label.commonCopy', [modelLabel]),
    handler: () => {
      $coreConfirm($i18nBundle('common.msg.commonConfirm', [$i18nBundle('common.label.commonCopy', [modelLabel])]))
        .then(() => copyApiModel(row.id, { loading: true }))
        .then(result => {
          if (result?.resultData?.id) {
            saveComponent(result.resultData)
          }
        })
    }
  }, {
    enabled: isWritable.value,
    icon: row.locked ? 'LockOpenFilled' : 'LockFilled',
    iconColor: row.locked ? 'var(--el-color-success)' : 'var(--el-color-warning)',
    label: lockLabel,
    handler: () => {
      $coreConfirm($i18nBundle('common.msg.commonConfirm', [lockLabel]))
        .then(() => ApiProjectInfoDetailApi.saveOrUpdate({ ...row, locked: !row.locked }, { loading: true }))
        .then(() => {
          if (currentInfoDetail.value?.id === row.id) {
            currentInfoDetail.value.locked = !row.locked
          }
          loadProjectComponents()
        })
    }
  }, {
    enabled: isDeletable.value,
    icon: 'Delete',
    type: 'danger',
    label: $i18nBundle('common.label.commonDelete', [modelLabel]),
    handler: () => {
      const alertMsg = row.schemaName ? $i18nBundle('common.msg.commonDeleteConfirm', [row.schemaName]) : $i18nBundle('common.msg.deleteConfirm')
      $coreConfirm(alertMsg)
        .then(() => ApiProjectInfoDetailApi.deleteById(row.id, { loading: true }))
        .then(() => {
          if (currentInfoDetail.value?.id === row.id) {
            currentInfoDetail.value = null
          }
          loadProjectComponents()
        })
    }
  }].filter(item => item.enabled !== false)
}

const handleRowContextMenu = (row, column, event) => {
  event.preventDefault()
  if (!isWritable.value && !isDeletable.value) {
    return
  }
  componentsTableRef.value?.table?.setCurrentRow(row)
  showContextMenu.value = false
  contextMenuHandlers.value = getRowHandlers(row)
  contextMenuPos.x = event.clientX
  contextMenuPos.y = event.clientY
  nextTick(() => {
    showContextMenu.value = true
    nextTick(() => {
      contextMenuDropdownRef.value?.handleOpen?.()
    })
  })
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
            {{ projectItem?.projectName }} - {{ $t('api.label.dataModel') }}
          </span>
        </el-container>
      </template>
    </el-page-header>
    <el-container
      v-loading="loading"
      style="height: calc(100% - 60px);"
    >
      <div class="form-edit-width-100">
        <common-form
          inline
          :model="searchParam"
          :options="searchFormOptions"
          :back-url="goBack"
          :submit-label="$t('common.label.search')"
          @submit-form="loadProjectComponents"
        >
          <template #buttons>
            <el-button
              v-if="isWritable"
              type="info"
              @click="newOrEdit()"
            >
              {{ $t('common.label.new') }}
            </el-button>
            <el-button
              v-if="isDeletable"
              type="danger"
              @click="confirmRemoveAllByQuery()"
            >
              {{ $t('api.label.clearComponents') }}
            </el-button>
          </template>
        </common-form>
        <common-split
          v-if="projectItem"
          :sizes="splitSizes"
          :min-size="defaultMinSizes"
          class="height100"
        >
          <template #split-0>
            <common-table
              ref="componentsTableRef"
              v-model:page="searchParam.page"
              class="request-table"
              :data="tableData"
              :buttons-column-attrs="{minWidth:'100px'}"
              :columns="columns"
              :loading="loading"
              :page-attrs="pageAttrs"
              @page-size-change="loadProjectComponents()"
              @current-page-change="loadProjectComponents()"
              @current-change="$event?newOrEdit($event?.id):undefined"
              @row-contextmenu="handleRowContextMenu"
            >
              <template #buttonHeader>
                {{ $t('api.label.dataModel') }}
                <el-tag
                  v-if="searchParam.page?.totalCount"
                  :title="$t('api.label.dataModel')"
                  class="margin-left1 pointer"
                  type="primary"
                  size="small"
                  effect="plain"
                  round
                >
                  {{ searchParam.page?.totalCount }}
                </el-tag>
              </template>
            </common-table>
          </template>
          <template #split-1>
            <api-project-component
              v-if="currentInfoDetail"
              v-model="currentInfoDetail"
              :current-project="projectItem"
              @save-component="saveComponent"
              @delete-component="loadProjectComponents()"
            />
          </template>
        </common-split>
      </div>
    </el-container>
    <div
      v-if="showContextMenu"
      style="position: fixed; pointer-events: none; z-index: 3000;"
      :style="{ left: contextMenuPos.x + 'px', top: contextMenuPos.y + 'px' }"
    >
      <el-dropdown
        ref="contextMenuDropdownRef"
        trigger="contextmenu"
        @visible-change="handleContextMenuVisibleChange"
      >
        <span style="display: inline-block; width: 0; height: 0;" />
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="(handler, index) in contextMenuHandlers"
              :key="index"
              :disabled="handler.disabled"
              :divided="handler.divided"
              @click="handleContextItemClick(handler)"
            >
              <el-link
                underline="never"
                :type="handler.type || 'default'"
              >
                <common-icon
                  :icon="handler.icon"
                  :color="handler.iconColor"
                />
                {{ handler.label || $t(handler.labelKey) }}
              </el-link>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </el-container>
</template>

<style scoped>

</style>
