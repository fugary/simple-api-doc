import { useResourceApi } from '@/hooks/ApiHooks'
import { $httpGet } from '@/vendors/axios'

const BASE_URL = '/admin/docs'

const ApiDocApi = useResourceApi(BASE_URL)

export const loadDoc = function (docId, config) {
  return $httpGet(`${BASE_URL}/loadDoc/${docId}`, config)
}

export default ApiDocApi
