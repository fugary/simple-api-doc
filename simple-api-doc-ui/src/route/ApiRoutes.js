const BASE_PATH = '/api'

export default [{
  path: `${BASE_PATH}/projects`,
  name: 'MockProjectsNew',
  component: () => import('@/views/api/ApiProjects.vue'),
  meta: {
    replaceTabHistory: 'MockProjectsNew',
    labelKey: 'mock.label.mockProjects',
    icon: 'MessageBox'
  }
}]
