<script setup>
import { computed, h, onUnmounted, ref, toRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isEqual } from 'lodash-es'
import { ElMessage } from 'element-plus'
import { $i18nBundle } from '@/messages'
import { searchDocs } from '@/api/ApiDocSearchApi'
import { useDocProjectSelector } from '@/services/api/ApiDocSearchService'
import { useDocSearchStore } from '@/stores/DocSearchStore'
import { useLoginConfigStore } from '@/stores/LoginConfigStore'
import { useSearchStatus } from '@/consts/GlobalConstants'
import { ALL_METHODS } from '@/consts/ApiConstants'
import ApiMethodTag from '@/views/components/api/doc/ApiMethodTag.vue'
import TreeIconLabel from '@/views/components/utils/TreeIconLabel.vue'
import HighlightedText from '@/views/components/utils/HighlightedText.vue'
import ApiDocAiChatPanel from '@/views/components/api/doc/comp/ApiDocAiChatPanel.vue'
import { getAiStatus } from '@/api/AiCacheApi'
import { calcNodeLeaf } from '@/services/api/ApiFolderService'
import emitter from '@/vendors/emitter'

const props = defineProps({
  project: { type: Object, default: undefined },
  shareId: { type: String, default: undefined },
  onSelectDoc: { type: Function, default: undefined }
})
const router = useRouter()
const route = useRoute()
const showWindow = ref(true)

const aiConfigs = ref([])
const defaultAiConfigId = ref(null)
const aiEnabled = ref(false)

if (!props.shareId) {
  getAiStatus().then(res => {
    if (res && res.success) {
      const data = res.resultData || {}
      aiEnabled.value = !!data.enabled
      aiConfigs.value = data.configs || []
      defaultAiConfigId.value = data.defaultConfigId || null
    }
  }).catch(() => {
    aiEnabled.value = false
  })
}

const canUseAi = computed(() => !props.shareId && aiEnabled.value && aiConfigs.value.length > 0)
const tabOptions = computed(() => [
  {
    value: 'search',
    label: $i18nBundle('api.label.advancedSearch'),
    icon: 'Search'
  },
  {
    value: 'ai',
    label: $i18nBundle('api.label.aiAssistant'),
    icon: 'ChatDotRound'
  }
])

const searchStore = useDocSearchStore()
const loginStore = useLoginConfigStore()
const sessionKey = props.shareId ? `share:${props.shareId}` : `${loginStore.accountInfo?.id}:${props.project?.id || 'all'}`
const defaultModel = () => ({
  scope: props.shareId ? 'share' : props.project ? 'selected' : 'all',
  projectId: props.project?.id,
  docName: '',
  url: '',
  content: '',
  docType: undefined,
  method: undefined,
  status: undefined
})
const defaultAiState = () => ({
  includeProjectOverview: false,
  configId: null,
  model: '',
  projectId: props.project?.id || null,
  queryInput: '',
  toolSteps: [],
  relatedDocs: [],
  answerContent: '',
  isStepsExpanded: true
})

searchStore.sessions[sessionKey] ||= {
  model: defaultModel(),
  submitted: null,
  records: [],
  searched: false,
  more: false,
  page: { pageNumber: 1, pageSize: 20, totalCount: 0 },
  activeTab: 'search',
  aiState: defaultAiState()
}
const session = searchStore.sessions[sessionKey]
session.activeTab ||= 'search'
session.aiState ||= defaultAiState()

const activeTab = toRef(session, 'activeTab')
// 分页控件先更新本地状态，只有请求成功后才保存为结果对应的页码。
const tablePage = ref(session.page)
const loading = ref(false)
const failed = ref(false)
session.projectOptions ||= []
const projects = toRef(session, 'projectOptions')
const includeContextProject = () => {
  if (props.project && !projects.value.some(project => project.id === props.project.id)) {
    projects.value.unshift({ id: props.project.id, projectName: props.project.projectName })
  }
}
includeContextProject()
const { loadProjects, projectSelectAttrs } = useDocProjectSelector(() => session.model.projectId, projects)
let searchVersion = 0

const requestModel = computed(() => {
  const model = session.model
  return {
    projectId: !props.shareId && model.scope === 'selected' ? model.projectId : undefined,
    docName: model.docName.trim() || undefined,
    url: model.url.trim() || undefined,
    content: model.content.trim() || undefined,
    docType: model.url.trim() || model.method ? 'api' : model.docType || undefined,
    method: model.method || undefined,
    status: props.shareId ? undefined : model.status
  }
})
const conditionsChanged = computed(() => !!session.submitted && !isEqual(requestModel.value, session.submitted))
watch(() => [session.model.url, session.model.method], ([url, method]) => {
  if (url.trim() || method) session.model.docType = 'api'
})

const docTypeLabel = ({ value, label }) => h(TreeIconLabel, {
  node: { isLeaf: true, label },
  iconLeaf: calcNodeLeaf({ isDoc: true, docType: value })
})

const options = computed(() => [{
  labelKey: 'api.label.searchScope',
  prop: 'scope',
  type: 'select',
  disabled: !!props.shareId,
  attrs: { clearable: false },
  children: props.shareId
    ? [{ value: 'share', label: $i18nBundle('api.label.searchShare') }]
    : [
        { value: 'all', label: $i18nBundle('api.label.searchAllProjects') },
        { value: 'selected', label: $i18nBundle('api.label.searchSelectedProject') }
      ],
  change: value => { if (value === 'selected') loadProjects() }
}, {
  labelKey: 'api.label.project',
  prop: 'projectId',
  type: 'select',
  enabled: session.model.scope === 'selected' && !props.shareId,
  attrs: projectSelectAttrs.value,
  children: projects.value.map(project => ({ value: project.id, label: project.projectName }))
}, {
  labelKey: 'api.label.docName', prop: 'docName', attrs: { maxlength: 200 }
}, {
  labelKey: 'api.label.searchUrl', prop: 'url', attrs: { maxlength: 1000 }
}, {
  labelKey: 'api.label.searchContent',
  prop: 'content',
  attrs: { maxlength: 500 },
  tooltip: $i18nBundle('api.msg.searchContentHelp')
}, {
  labelKey: 'api.label.searchDocType',
  prop: 'docType',
  type: 'select',
  disabled: !!(session.model.url.trim() || session.model.method),
  slots: { label: docTypeLabel },
  children: [{ value: 'api', label: $i18nBundle('api.label.searchApi') }, { value: 'md', label: 'Markdown' }]
    .map(type => ({ ...type, slots: { default: () => docTypeLabel(type) } }))
}, {
  labelKey: 'api.label.method',
  prop: 'method',
  type: 'select',
  enabled: session.more,
  children: ALL_METHODS.map(({ method }) => ({ value: method, label: method }))
}, {
  ...useSearchStatus({}), enabled: session.more && !props.shareId
}])

const columns = [
  { labelKey: 'api.label.project', slot: 'project', minWidth: 180 },
  { labelKey: 'api.label.docName', slot: 'document', minWidth: 400 }
]

const runSearch = async (pageNumber = 1, useSubmitted = false, pageSize = session.page.pageSize) => {
  if (loading.value) return
  if (!useSubmitted && session.model.scope === 'selected' && !session.model.projectId && !props.shareId) {
    ElMessage.warning($i18nBundle('api.msg.searchChooseProject'))
    return
  }
  const version = ++searchVersion
  const submitted = { ...(useSubmitted ? session.submitted : requestModel.value) }
  loading.value = true
  failed.value = false
  try {
    const data = await searchDocs({ ...submitted, page: { pageNumber, pageSize } }, props.shareId)
    if (version !== searchVersion) return
    if (!data?.success) throw new Error('Search failed')
    session.records = data.resultData || []
    session.page = data.page
    session.submitted = submitted
    session.searched = true
  } catch {
    if (version === searchVersion) failed.value = true
  } finally {
    if (version === searchVersion) {
      tablePage.value = session.page
      loading.value = false
    }
  }
}
const reset = () => {
  searchVersion++
  loading.value = false
  failed.value = false
  Object.assign(session, { model: defaultModel(), submitted: null, searched: false, records: [], page: { pageNumber: 1, pageSize: 20, totalCount: 0 } })
  tablePage.value = session.page
  includeContextProject()
}
const openDoc = async (doc) => {
  if (props.onSelectDoc && (props.shareId || doc.projectId === props.project?.id)) {
    await props.onSelectDoc(doc)
  } else if (route.params.projectCode === doc.projectCode) {
    emitter.emit('select-search-doc', doc)
  } else {
    await router.push({ path: `/api/projects/${doc.projectCode}`, query: { docId: doc.id } })
  }
  showWindow.value = false
}
const handleSwitchToSearch = (keyword) => {
  activeTab.value = 'search'
  if (keyword) {
    session.model.docName = keyword
    runSearch()
  }
}
onUnmounted(() => {
  searchVersion++
})
if (session.model.scope === 'selected') loadProjects()
</script>

<template>
  <common-window
    v-model="showWindow"
    :title="canUseAi && activeTab === 'ai' ? $t('api.label.aiAssistant') : $t('api.label.advancedSearch')"
    width="min(960px, 96vw)"
    :show-buttons="false"
    :close-on-click-modal="false"
    close-on-press-escape
    show-fullscreen
    append-to-body
  >
    <div class="doc-search-window">
      <div
        v-if="canUseAi"
        class="search-tab-header"
      >
        <el-segmented
          v-model="activeTab"
          :options="tabOptions"
        >
          <template #default="{ item }">
            <div class="search-segment-item">
              <common-icon :icon="item.icon" />
              <span>{{ item.label }}</span>
            </div>
          </template>
        </el-segmented>
      </div>

      <div
        v-show="!canUseAi || activeTab === 'search'"
        class="doc-search-pane"
      >
        <common-form
          class="doc-search-form"
          :model="session.model"
          :options="options"
          label-width="auto"
          inline
          :submit-label="$t('common.label.search')"
          :disable-buttons="loading"
          @submit-form="runSearch()"
        >
          <template #buttons>
            <el-button @click="reset">
              {{ $t('common.label.reset') }}
            </el-button>
            <el-button
              link
              type="primary"
              @click="session.more = !session.more"
            >
              {{ $t(session.more ? 'api.label.searchLess' : 'api.label.searchMore') }}
            </el-button>
            <el-text
              v-if="conditionsChanged && !loading"
              v-common-tooltip="$t('api.msg.searchChanged')"
              class="doc-search-hint"
              type="info"
              size="small"
              role="status"
            >
              <common-icon icon="InfoFilled" />
              {{ $t('api.label.searchPending') }}
            </el-text>
          </template>
        </common-form>
        <el-alert
          v-if="failed"
          type="error"
          :closable="false"
          :title="$t('api.msg.searchFailed')"
        />
        <common-table
          v-model:page="tablePage"
          :columns="columns"
          :data="session.records"
          :loading="loading"
          row-key="id"
          max-height="45vh"
          @current-page-change="runSearch($event, true)"
          @page-size-change="runSearch(1, true, $event)"
        >
          <template #empty>
            {{ $t(session.searched ? 'common.msg.noData' : 'api.msg.searchStart') }}
          </template>
          <template #project="{ item: doc }">
            <div>{{ doc.projectName }}</div>
            <el-text
              type="info"
              size="small"
            >
              {{ doc.folderPath }}
            </el-text>
          </template>
          <template #document="{ item: doc }">
            <tree-icon-label
              class="doc-search-title"
              :node="{ isLeaf: true, label: doc.docName }"
              :icon-leaf="calcNodeLeaf({ isDoc: true, docType: doc.docType })"
            >
              <api-method-tag
                v-if="doc.docType === 'api'"
                :method="doc.method"
              />
              <el-link
                type="primary"
                underline="hover"
                @click="openDoc(doc)"
              >
                <highlighted-text
                  :text="doc.docName"
                  :keyword="session.submitted?.docName"
                />
              </el-link>
              <el-tag
                v-if="doc.status === 0"
                size="small"
                type="info"
              >
                {{ $t('common.label.statusDisabled') }}
              </el-tag>
            </tree-icon-label>
            <div
              v-if="doc.url"
              class="doc-search-url"
            >
              <highlighted-text
                :text="doc.url"
                :keyword="session.submitted?.url"
              />
            </div>
            <div
              v-if="doc.snippet"
              class="doc-search-snippet"
            >
              <el-text
                type="info"
                size="small"
              >
                {{ $t('api.label.searchContent') }}：
              </el-text>
              <highlighted-text
                :text="doc.snippet"
                :keyword="session.submitted?.content"
              />
            </div>
          </template>
        </common-table>
      </div>

      <div
        v-if="canUseAi"
        v-show="activeTab === 'ai'"
        class="doc-search-pane"
      >
        <api-doc-ai-chat-panel
          :project="props.project"
          :ai-state="session.aiState"
          :ai-configs="aiConfigs"
          :default-config-id="defaultAiConfigId"
          :on-select-doc="openDoc"
          @switch-to-search="handleSwitchToSearch"
        />
      </div>
    </div>
  </common-window>
</template>

<style scoped>
.doc-search-window { width: 100%; min-width: 0; }
.search-tab-header { margin-bottom: 14px; }
.search-segment-item { display: inline-flex; align-items: center; gap: 6px; padding: 2px 4px; font-size: 13px; }
.search-segment-item :deep(.el-icon) { font-size: 14px; }
.doc-search-form :deep(.el-form) { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 24px; }
.doc-search-form :deep(.el-form-item) { margin-right: 0; min-width: 0; }
.doc-search-form :deep(.el-input), .doc-search-form :deep(.el-select) { width: 100%; }
.doc-search-window :deep(.cell) { overflow-wrap: anywhere; }
.doc-search-title { max-width: 100%; }
.doc-search-hint { display: inline-flex; align-items: center; gap: 4px; margin-left: 12px; }
.doc-search-url { color: var(--el-text-color-secondary); font-family: monospace; }
.doc-search-snippet { margin-top: 4px; font-size: 13px; }
.doc-search-window :deep(.common-pagination) { flex-wrap: wrap; row-gap: 8px; }
@media (max-width: 768px) {
  .doc-search-form :deep(.el-form) { grid-template-columns: minmax(0, 1fr); }
}
</style>
