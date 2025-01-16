import { useResourceApi } from '@/hooks/ApiHooks'
import { getPathUrl } from '@/utils'
import { $http } from '@/vendors/axios'

const SHARE_BASE_URL = '/admin/shares'

const ApiProjectShareApi = useResourceApi(SHARE_BASE_URL)

/**
 * 分组复制
 * @param id
 * @param [config]
 * @returns {Promise<axios.AxiosResponse<any>>}
 */
export const copyProjectShare = (id, config) => {
  return $http(Object.assign({
    url: `${SHARE_BASE_URL}/copy/${id}`,
    method: 'POST'
  }, config)).then(response => response.data?.resultData)
}

/**
 * 获取分享地址地址信息
 * @param shareId
 * @return {String}
 */
export const getShareUrl = (shareId) => {
  return getPathUrl(`/share/${shareId}`)
}

export default ApiProjectShareApi
