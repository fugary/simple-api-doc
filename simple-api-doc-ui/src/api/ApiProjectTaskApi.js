import { useResourceApi } from '@/hooks/ApiHooks'
import { $httpPost } from '@/vendors/axios'

const BASE_URL = '/admin/tasks'

const ApiProjectTaskApi = useResourceApi(BASE_URL)

export const triggerTask = function (taskId, config) {
  return $httpPost(`${BASE_URL}/trigger/${taskId}`, null, config)
}

export default ApiProjectTaskApi
