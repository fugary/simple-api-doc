import { $httpGet } from '@/vendors/axios'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import { $downloadWithLinkClick } from '@/utils'
import { BASE_URL } from '@/config'

const SHARE_BASE_URL = '/shares'

const getShareConfig = (shareId) => {
  const shareConfigStore = useShareConfigStore()
  return {
    addToken: false,
    headers: {
      Authorization: `Bearer ${shareConfigStore.getShareToken(shareId)}`
    }
  }
}
/**
 * 获取envConfigs
 *
 * @param apiDocDetail
 * @returns {any|*[]}
 */
export const getEnvConfigs = (apiDocDetail) => {
  const apiShare = apiDocDetail?.apiShare
  const projectInfoDetail = apiDocDetail?.projectInfoDetail
  const envContent = apiShare?.envContent || projectInfoDetail?.envContent
  if (envContent) {
    return JSON.parse(envContent) || []
  }
  return []
}

export const loadShare = function ({ shareId, password }, config) {
  return $httpGet(`${SHARE_BASE_URL}/loadShare/${shareId}?pwd=${password}`, config)
}

export const loadShareDoc = function ({ shareId, docId }, config) {
  config = Object.assign(getShareConfig(shareId), config || {})
  return $httpGet(`${SHARE_BASE_URL}/loadShareDoc/${shareId}/${docId}`, config)
}

export const loadProject = function (shareId, config) {
  config = Object.assign(getShareConfig(shareId), config || {})
  return $httpGet(`${SHARE_BASE_URL}/loadProject/${shareId}`, config)
}

export const checkDownloadDocs = function (shareId, config) {
  config = Object.assign(getShareConfig(shareId), config || {})
  return $httpGet(`${SHARE_BASE_URL}/checkDownloadDocs/${shareId}`, config)
}

export const downloadShareDocs = function ({ type, shareId }) {
  const shareConfigStore = useShareConfigStore()
  const downloadUrl = `${BASE_URL}${SHARE_BASE_URL}/download/${type}/${shareId}?access_token=${shareConfigStore.getShareToken(shareId)}`
  $downloadWithLinkClick(downloadUrl)
}
