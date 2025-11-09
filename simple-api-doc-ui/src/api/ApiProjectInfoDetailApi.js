import { useResourceApi } from '@/hooks/ApiHooks'
import { $http } from '@/vendors/axios'

const BASE_URL = '/admin/info/detail'

const ApiProjectInfoDetailApi = useResourceApi(BASE_URL)

/**
 * 加载ProjectInfoDetails信息
 * @return {Promise<T>}
 */
export const loadInfoDetails = (data, config) => {
  return $http(Object.assign({
    url: `${BASE_URL}/loadInfoDetails`,
    method: 'POST',
    data
  }, config)).then(response => response.data?.resultData)
}

export default ApiProjectInfoDetailApi
