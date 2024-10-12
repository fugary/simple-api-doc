import { useResourceApi } from '@/hooks/ApiHooks'
import { $http } from '@/vendors/axios'

const BASE_URL = '/admin/simple-tasks'

const SimpleTaskApi = useResourceApi(BASE_URL)

/**
 * 删除并禁用
 *
 * @param taskId
 * @param tid
 * @param [config]
 * @returns {Promise<axios.AxiosResponse<any>>}
 */
export const removeAndDisable = function ({ taskId, tid }, config) {
  return $http(Object.assign({
    url: `${BASE_URL}/${taskId}/${tid}`,
    method: 'delete'
  }, config)).then(response => response.data)
}

export default SimpleTaskApi
