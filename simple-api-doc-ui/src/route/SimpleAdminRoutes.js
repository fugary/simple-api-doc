import { createNewComponent } from '@/utils/DynamicUtils'

const BASE_PATH = '/admin'

export default [{
  path: `${BASE_PATH}/simple-tasks`,
  name: 'SimpleTasks',
  component: () => import('@/views/admin/tasks/SimpleTasks.vue'),
  meta: {
    icon: 'Timer',
    labelKey: 'api.label.taskManagement'
  }
}, {
  path: `${BASE_PATH}/logs`,
  name: 'SimpleLogs',
  component: () => import('@/views/admin/apiLogs/SimpleLogs.vue'),
  meta: {
    icon: 'Document',
    labelKey: 'api.label.logManagement'
  }
}, {
  path: `${BASE_PATH}/shares`,
  name: 'AdminProjectShares',
  component: createNewComponent('AdminProjectShares', () => import('@/views/api/project/ApiProjectShares.vue')),
  meta: {
    icon: 'Share',
    labelKey: 'api.label.shareManagement'
  }
}, {
  path: `${BASE_PATH}/imports`,
  name: 'ApiProjectImportTasks',
  component: createNewComponent('ApiProjectImportTasks', () => import('@/views/api/project/ApiProjectTasks.vue')),
  meta: {
    icon: 'InputFilled',
    labelKey: 'api.label.importManagement'
  }
}, {
  path: `${BASE_PATH}/groups`,
  name: 'ApiProjectGroups',
  component: () => import('@/views/api/project/ApiProjectGroups.vue'),
  meta: {
    icon: 'Grid',
    labelKey: 'api.label.projectGroups'
  }
}]
