const BASE_PATH = '/api'

export default [{
  path: `${BASE_PATH}/projects`,
  name: 'ApiProjectsBase',
  children: [{
    path: '',
    name: 'ApiProjects',
    component: () => import('@/views/api/project/ApiProjects.vue'),
    meta: {
      replaceTabHistory: 'ApiProjects',
      labelKey: 'api.label.apiProjects',
      icon: 'MessageBox'
    }
  }, {
    path: ':projectCode',
    name: 'ApiProjectEdit',
    component: () => import('@/views/api/project/ApiProjectEdit.vue'),
    meta: {
      replaceTabHistory: 'ApiProjects',
      labelKey: 'api.label.apiProjectDetails',
      icon: 'Edit',
      hideCopyRight: true
    }
  }, {
    path: 'shares/:projectCode',
    name: 'ApiProjectShares',
    component: () => import('@/views/api/project/ApiProjectShares.vue'),
    meta: {
      replaceTabHistory: 'ApiProjects',
      labelKey: 'api.label.shareDocs',
      icon: 'Share'
    }
  }, {
    path: 'tasks/:projectCode',
    name: 'ApiProjectTasks',
    component: () => import('@/views/api/project/ApiProjectTasks.vue'),
    meta: {
      replaceTabHistory: 'ApiProjects',
      labelKey: 'api.label.importData',
      icon: 'InputFilled'
    }
  }, {
    path: 'components/:projectCode',
    name: 'ApiProjectComponents',
    component: () => import('@/views/api/project/ApiProjectComponents.vue'),
    meta: {
      replaceTabHistory: 'ApiProjects',
      labelKey: 'api.label.dataModel',
      icon: 'SettingsInputComponentOutlined'
    }
  }]
}]
