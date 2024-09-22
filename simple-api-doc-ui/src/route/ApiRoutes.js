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
      labelKey: 'api.label.apiProjects',
      icon: 'List'
    }
  }]
}]
