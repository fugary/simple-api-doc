import { $httpGet } from '@/vendors/axios'
import { useShareConfigStore } from '@/stores/ShareConfigStore'

const BASE_URL = '/shares'

const getShareConfig = (shareId) => {
  const shareConfigStore = useShareConfigStore()
  return {
    addToken: false,
    headers: {
      Authorization: `Bearer ${shareConfigStore.getShareToken(shareId)}`
    }
  }
}

export const loadShare = function ({ shareId, password }, config) {
  return $httpGet(`${BASE_URL}/loadShare/${shareId}?pwd=${password}`, config)
}

export const loadShareDoc = function ({ shareId, docId }, config) {
  config = Object.assign(getShareConfig(shareId), config || {})
  return $httpGet(`${BASE_URL}/loadShareDoc/${shareId}/${docId}`, config)
}

export const loadProject = function (shareId, config) {
  config = Object.assign(getShareConfig(shareId), config || {})
  return $httpGet(`${BASE_URL}/loadProject/${shareId}`, config)
}
