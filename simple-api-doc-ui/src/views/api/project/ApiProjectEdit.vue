<script setup lang="jsx">
import { useRoute } from 'vue-router'
import { useBackUrl } from '@/utils'
import { computed, ref, watch } from 'vue'
import { useApiProjectItem } from '@/api/ApiProjectApi'
import ApiProjectImport from '@/views/components/api/ApiProjectImport.vue'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import MarkdownDocViewer from '@/views/components/api/doc/MarkdownDocViewer.vue'
import ApiDocViewer from '@/views/components/api/doc/ApiDocViewer.vue'
import { calcProjectItem, filerProjectItem } from '@/services/api/ApiProjectService'

const route = useRoute()
const projectCode = route.params.projectCode

const { goBack } = useBackUrl('/api/projects')
const { projectItem, loadSuccess, loading } = useApiProjectItem(projectCode)

//* ************搜索框**************//
const searchParam = ref({})
const searchFormOptions = computed(() => {
  return [
    {
      labelKey: 'common.label.keywords',
      prop: 'keyword',
      attrs: {
        onInput: calcProjectItemInfo
      }
    }
  ]
})
const showImportWindow = ref(false)
const currentDoc = ref(null)
const treeNodes = ref([])
const defaultExpandedKeys = ref([])
const currentNodeKey = ref(null)

const calcProjectItemInfo = () => {
  const { docTreeNodes, docExpandedKeys, currentSelectDoc } = calcProjectItem(filerProjectItem(projectItem.value, searchParam.value.keyword), currentDoc.value)
  currentDoc.value = currentSelectDoc
  treeNodes.value = docTreeNodes
  defaultExpandedKeys.value = docExpandedKeys
}

watch(projectItem, (apiProject) => {
  if (apiProject) {
    calcProjectItemInfo()
  }
})

const showDocDetails = (doc) => {
  console.log('====================================doc', doc)
  if (doc.isDoc) {
    currentDoc.value = doc
  }
}
</script>

<template>
  <el-container class="flex-column">
    <el-page-header @back="goBack">
      <template #content>
        <span class="text-large font-600 mr-3">
          {{ projectItem?.projectName }} 【{{ projectItem?.projectCode }}】
        </span>
      </template>
    </el-page-header>
    <common-form
      class="margin-top3"
      inline
      :model="searchParam"
      :options="searchFormOptions"
      :show-submit="false"
    >
      <template #buttons>
        <el-button
          type="success"
          @click="showImportWindow = true"
        >
          {{ $t('api.label.import') }}
        </el-button>
        <el-button
          @click="goBack()"
        >
          {{ $t('common.label.back') }}
        </el-button>
      </template>
    </common-form>
    <el-container v-if="loadSuccess">
      <div class="form-edit-width-100">
        <common-split
          :min-size="150"
          :max-size="[500, Infinity]"
        >
          <template #split-0>
            <div class="padding-right2">
              <el-card
                shadow="never"
                class="small-card operation-card"
              >
                <template #header>
                  接口管理
                </template>
                <el-tree
                  v-loading="loading"
                  node-key="id"
                  :data="treeNodes"
                  :default-expanded-keys="defaultExpandedKeys"
                  :accordion="true"
                  highlight-current
                  :current-node-key="currentNodeKey"
                  @node-click="showDocDetails"
                >
                  <template #empty>
                    <el-empty :description="$t('common.msg.noData')" />
                  </template>
                  <template #default="{node}">
                    <!--                    <el-dropdown-->
                    <!--                      :id="`${data.value}`"-->
                    <!--                      :ref="dropdownRef => groupTreeDropdownRef.push(dropdownRef)"-->
                    <!--                      trigger="contextmenu"-->
                    <!--                    >-->
                    <tree-icon-label :node="node" />
                    <!--                      <template #dropdown>-->
                    <!--                        <el-dropdown-menu>-->
                    <!--                          <el-dropdown-item @click="handleTreeNodeOperation('create', data);currentNodeKey=data.value">-->
                    <!--                            {{ $i18nKey('common.label.commonAdd', 'admin.label.department') }}-->
                    <!--                          </el-dropdown-item>-->
                    <!--                          <el-dropdown-item @click="handleTreeNodeOperation('delete', data);">-->
                    <!--                            {{ $i18nKey('common.label.commonDelete', 'admin.label.department') }}-->
                    <!--                          </el-dropdown-item>-->
                    <!--                          <el-dropdown-item @click="handleTreeNodeOperation('cancel', data)">-->
                    <!--                            {{ $t('common.label.close') }}-->
                    <!--                          </el-dropdown-item>-->
                    <!--                        </el-dropdown-menu>-->
                    <!--                      </template>-->
                    <!--                    </el-dropdown>-->
                  </template>
                </el-tree>
              </el-card>
            </div>
          </template>
          <template #split-1>
            <markdown-doc-viewer
              v-if="currentDoc?.docType==='md'&&currentDoc?.docContent"
              v-model="currentDoc.docContent"
            />
            <api-doc-viewer
              v-if="currentDoc?.docType==='api'"
              v-model="currentDoc"
            />
          </template>
        </common-split>
      </div>
    </el-container>
    <api-project-import
      v-model="showImportWindow"
      :project="projectItem"
    />
  </el-container>
</template>

<style scoped>

</style>
