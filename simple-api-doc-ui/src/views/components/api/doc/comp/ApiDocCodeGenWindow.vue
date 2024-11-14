<script setup>
import SwaggerCodeGen from '@/views/components/api/doc/comp/SwaggerCodeGen.vue'
import { computed, ref } from 'vue'
import TreeCheckConfig from '@/views/components/utils/TreeCheckConfig.vue'
import { $i18nBundle } from '@/messages'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import { calcNodeLeaf, calcPreferenceId } from '@/services/api/ApiFolderService'
import { cloneDeep, isNumber } from 'lodash-es'
import { generateCode } from '@/api/SwaggerGeneratorApi'
import { $coreConfirm, $coreHideLoading, $coreShowLoading, $downloadWithLinkClick } from '@/utils'
import { checkExportDownloadDocs } from '@/api/SimpleShareApi'
import { checkExportProjectDocs } from '@/api/ApiProjectApi'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'

const TABS = {
  TREE: 'tree',
  CONFIG: 'config'
}

const props = defineProps({
  shareDoc: {
    type: Object,
    default: undefined
  },
  projectItem: {
    type: Object,
    default: undefined
  },
  treeNodes: {
    type: Array,
    default: () => []
  }
})

const showCodeGenConfigWindow = defineModel({
  type: Boolean,
  default: false
})

const treeSelectKeys = defineModel('treeSelectKeys', {
  type: Array,
  default: () => []
})

const shareConfigStore = useShareConfigStore()
const preferenceId = calcPreferenceId(props.projectItem, props.shareDoc)
const getSavedGenParam = () => {
  return cloneDeep(shareConfigStore.shareGenerateCodeConfig[preferenceId] || {
    _language: 'java'
  })
}
const codeGenParamModel = ref(getSavedGenParam())

const currentTab = ref(shareConfigStore.shareGenerateCodeConfig[preferenceId] ? TABS.TREE : TABS.CONFIG)
const okButtonLabel = computed(() => {
  if (currentTab.value === TABS.CONFIG) {
    return $i18nBundle('common.label.save')
  }
  return $i18nBundle(treeSelectKeys.value?.length ? 'common.label.generateSelected' : 'common.label.generateAll')
})

const okButtonClick = () => {
  if (currentTab.value === TABS.CONFIG) {
    console.log('===============================save', currentTab.value, codeGenParamModel.value)
    shareConfigStore.shareGenerateCodeConfig[preferenceId] = cloneDeep(codeGenParamModel.value)
    currentTab.value = TABS.TREE
  } else {
    $coreConfirm($i18nBundle('api.msg.generateCodeConfirm')).then(() => {
      console.log('===============================generate', currentTab.value, codeGenParamModel.value)
      const paramModel = getSavedGenParam()
      $coreShowLoading()
      generateSelectedDocs(treeSelectKeys.value).then(data => {
        return generateCode(paramModel._language, {
          spec: JSON.parse(data),
          options: { ...paramModel, _language: undefined }
        }).then(data => {
          if (data.link) {
            $downloadWithLinkClick(data.link)
          }
        })
      }).finally(() => $coreHideLoading())
    })
  }
  return false
}

const generateSelectedDocs = (data) => {
  const param = {
    shareId: props.shareDoc?.shareId,
    projectCode: props.projectItem?.projectCode,
    type: props.exportType,
    returnContent: true
  }
  const isShareDoc = !!props.shareDoc?.shareId
  const checkDownloadFunc = isShareDoc ? checkExportDownloadDocs : checkExportProjectDocs
  const docIds = data?.filter(id => isNumber(id))
  return checkDownloadFunc({
    ...param,
    docIds
  }).then(resData => {
    if (resData.success && resData.resultData) {
      return resData.resultData
    }
  })
}

</script>

<template>
  <common-window
    v-model="showCodeGenConfigWindow"
    :title="$t('api.label.generateClientCode')"
    :ok-label="okButtonLabel"
    :ok-click="okButtonClick"
  >
    <template #header>
      {{ $t('api.label.generateClientCode') }}
      <el-link
        :underline="false"
        href="https://generator.swagger.io"
        target="_blank"
      >
        {{ $t('api.label.generatorProvider') }}: {{ $t('api.label.generatorProviderSwagger') }}
      </el-link>
    </template>
    <el-tabs
      v-model="currentTab"
      class="form-edit-width-100"
    >
      <el-tab-pane
        lazy
        :name="TABS.TREE"
      >
        <template #label>
          {{ $t('api.label.selectToGenerate') }}
        </template>
        <tree-check-config
          v-model:selected-keys="treeSelectKeys"
          :tree-nodes="treeNodes"
          node-key="treeId"
          show-filter
          tree-height="400px"
        >
          <template #default="{node, data}">
            <tree-icon-label
              :show-icon="shareDoc?.showTreeIcon!==false"
              :node="node"
              :icon-leaf="calcNodeLeaf(data)"
            >
              <api-method-tag
                v-if="data.docType==='api'"
                :method="data.method"
              />
              {{ node.label }}
            </tree-icon-label>
          </template>
        </tree-check-config>
      </el-tab-pane>
      <el-tab-pane
        lazy
        :name="TABS.CONFIG"
      >
        <template #label>
          {{ $t('api.label.generateCodeConfig') }}
        </template>
        <el-container class="flex-column">
          <el-scrollbar
            class="form-edit-width-100"
            style="height:400px;"
          >
            <swagger-code-gen v-model="codeGenParamModel" />
          </el-scrollbar>
        </el-container>
      </el-tab-pane>
    </el-tabs>
  </common-window>
</template>

<style scoped>

</style>
