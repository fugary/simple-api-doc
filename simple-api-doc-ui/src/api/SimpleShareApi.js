import { $httpGet } from '@/vendors/axios'

const BASE_URL = '/shares'

export const loadShare = function (shareId, config) {
  return $httpGet(`${BASE_URL}/${shareId}`, config)
}

export const loadShareDoc = function ({ shareId, docId }, config) {
  return $httpGet(`${BASE_URL}/${shareId}/${docId}`, config)
}
