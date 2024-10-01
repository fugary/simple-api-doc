import { useResourceApi } from '@/hooks/ApiHooks'
import { getPathUrl } from '@/utils'

const SHARE_BASE_URL = '/admin/shares'

const ApiProjectShareApi = useResourceApi(SHARE_BASE_URL)

/**
 * 获取分享地址地址信息
 * @param shareId
 * @return {String}
 */
export const getShareUrl = (shareId) => {
  return getPathUrl(`/share/${shareId}`)
}

export default ApiProjectShareApi
