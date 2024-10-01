import { $httpGet } from '@/vendors/axios'

const BASE_URL = '/shares'

export const loadShare = function ({ shareId, password }, config) {
  return $httpGet(`${BASE_URL}/loadShare/${shareId}?pwd=${password}`, config)
}

export const loadShareDoc = function ({ shareId, docId }, config) {
  return $httpGet(`${BASE_URL}/loadShareDoc/${shareId}/${docId}`, config)
}

export const loadProject = function (shareId, config) {
  return $httpGet(`${BASE_URL}/loadProject/${shareId}`, config)
}
