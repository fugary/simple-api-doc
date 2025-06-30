import { $httpGet, $httpPost } from '@/vendors/axios'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import { $downloadWithLinkClick } from '@/utils'
import { BASE_URL } from '@/config'
import { $i18nBundle } from '@/messages'

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
  const project = apiDocDetail?.project || apiDocDetail?.projectInfoDetail
  return mergeEnvConfigs(apiShare?.envContent, project?.envContent)
}

export const mergeEnvConfigs = (envContent, projectEnvContent) => {
  const projectConfigs = calcEnvConfigs(projectEnvContent)
  if (envContent) {
    const sharedUrls = calcEnvConfigs(envContent).map(item => item.url)
    return projectConfigs.filter(item => !sharedUrls.includes(item.url))
  }
  return projectConfigs
}

export const calcEnvConfigs = envContent => {
  const results = []
  const envs = (JSON.parse(envContent || '[]')) || []
  envs.filter(item => !item.disabled).forEach(item => {
    if (item.url) {
      const dupIndex = results.findIndex(resultItem => resultItem.url === item.url)
      if (dupIndex > -1) {
        results[dupIndex] = item
      } else {
        results.push(item)
      }
    }
  })
  return results
}

export const getEnvOptions = envConfigs => {
  return (envConfigs || []).map(env => {
    const label = env.name || $i18nBundle('api.label.defaultAddress')
    return {
      value: env.url,
      label,
      slots: {
        default: () => `${label} - ${env.url}`
      }
    }
  })
}

export const loadShare = function ({ shareId, password }, config) {
  config = Object.assign(getShareConfig(shareId), config || {})
  let loadShareUrl = `${SHARE_BASE_URL}/loadShare/${shareId}`
  if (password) {
    loadShareUrl = `${loadShareUrl}?pwd=${password}`
  }
  return $httpGet(loadShareUrl, config)
}

export const loadShareDoc = function ({ shareId, docId, markdown = false }, config) {
  config = Object.assign(getShareConfig(shareId), config || {})
  return $httpGet(`${SHARE_BASE_URL}/loadShareDoc/${shareId}/${docId}?md=${markdown}`, config)
}

export const loadMdDoc = function ({ shareId, docId }, config) {
  config = Object.assign(getShareConfig(shareId), config || {})
  return $httpGet(`${SHARE_BASE_URL}/loadMdDoc/${docId}`, config)
}

export const loadProject = function (shareId, config) {
  config = Object.assign(getShareConfig(shareId), config || {})
  return $httpGet(`${SHARE_BASE_URL}/loadProject/${shareId}`, config)
}

export const checkExportDownloadDocs = function (param, config) {
  const shareId = param.shareId
  config = Object.assign(getShareConfig(shareId), { loading: true, timeout: 60000 }, config || {})
  return $httpPost(`${SHARE_BASE_URL}/checkExportDownloadDocs`, param, config)
}

export const downloadExportShareDocs = function ({ type, shareId, uuid }) {
  const shareConfigStore = useShareConfigStore()
  const downloadUrl = `${BASE_URL}${SHARE_BASE_URL}/exportDownload/${type}/${shareId}/${uuid}?access_token=${shareConfigStore.getShareToken(shareId)}`
  $downloadWithLinkClick(downloadUrl)
}
