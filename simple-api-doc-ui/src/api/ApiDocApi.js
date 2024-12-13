import { useResourceApi } from '@/hooks/ApiHooks'
import { $httpGet, $httpPost } from '@/vendors/axios'

const BASE_URL = '/admin/docs'

const ApiDocApi = useResourceApi(BASE_URL)

export const loadDoc = function (docId, markdown = false, config) {
  return $httpGet(`${BASE_URL}/loadDoc/${docId}?md=${markdown}`, config)
}

export const loadHistoryList = function (param, config) {
  return $httpPost(`${BASE_URL}/historyList`, param, config)
}

export const loadHistoryDiff = function (param, config) {
  return $httpPost(`${BASE_URL}/loadHistoryDiff`, param, config)
}

export const copyApiDoc = function (docId, config) {
  return $httpPost(`${BASE_URL}/copyApiDoc/${docId}`, null, config)
}

export const updateApiDoc = function (apiDoc, config) {
  return $httpPost(`${BASE_URL}/updateApiDoc`, apiDoc, config)
}

export default ApiDocApi
