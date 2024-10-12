const BASE_PATH = '/admin'

export default [{
  path: `${BASE_PATH}/simple-tasks`,
  name: 'SimpleTasksBase',
  children: [{
    path: '',
    name: 'SimpleTasks',
    component: () => import('@/views/admin/tasks/SimpleTasks.vue')
  }]
}]
