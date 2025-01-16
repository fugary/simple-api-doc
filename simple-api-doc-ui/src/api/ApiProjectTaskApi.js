import { useResourceApi } from '@/hooks/ApiHooks'
import { $http, $httpPost } from '@/vendors/axios'

const BASE_URL = '/admin/tasks'

const ApiProjectTaskApi = useResourceApi(BASE_URL)

export const triggerTask = function (taskId, config) {
  return $httpPost(`${BASE_URL}/trigger/${taskId}`, null, config)
}

/**
 * 导入任务复制
 * @param id
 * @param [config]
 * @returns {Promise<axios.AxiosResponse<any>>}
 */
export const copyProjectTask = (id, config) => {
  return $http(Object.assign({
    url: `${BASE_URL}/copy/${id}`,
    method: 'POST'
  }, config)).then(response => response.data?.resultData)
}

export default ApiProjectTaskApi
