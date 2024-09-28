<script setup lang="jsx">
import { useRoute } from 'vue-router'
import { processTreeData, useBackUrl } from '@/utils'
import { computed, ref, watch } from 'vue'
import { useApiProjectItem } from '@/api/ApiProjectApi'
import ApiProjectImport from '@/views/components/api/ApiProjectImport.vue'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'

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
      prop: 'keyword'
    }
  ]
})
const showImportWindow = ref(false)
const currentDoc = ref(null)
const treeNodes = ref([])
const defaultExpandedKeys = ref([])
const currentNodeKey = ref(null)

watch(loadSuccess, () => {
  if (loadSuccess) {
    if (projectItem.value) {
      projectItem.value.docs?.sort((a, b) => {
        return a.sortId - b.sortId
      })
      currentDoc.value = projectItem.value.docs?.[0]
      treeNodes.value = processTreeData(projectItem.value.folders, null, {
        after (node) {
          node.label = node.folderName
        }
      })
      console.log('=============================treeNodes', treeNodes.value)
    }
  }
})
const filterFolderDocs = () => {
  console.log('filterFolderDocs')
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
      :submit-label="$t('common.label.search')"
      @submit-form="filterFolderDocs()"
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
                shadow="hover"
                class="small-card operation-card"
              >
                <template #header>
                  接口管理
                </template>
                <el-tree
                  v-loading="loading"
                  node-key="value"
                  :data="treeNodes"
                  :default-expanded-keys="defaultExpandedKeys"
                  :accordion="true"
                  highlight-current
                  :current-node-key="currentNodeKey"
                  @node-click="$goto(`/admin/groups/edit/modify/${$event.value}`)"
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
            {{ currentDoc }}
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
