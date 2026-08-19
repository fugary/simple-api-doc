import { $httpGet, $httpPost } from '@/vendors/axios'
import { useResourceApi } from '@/hooks/ApiHooks'

const BASE_URL = '/admin/ai/configs'

export const AiConfigApi = useResourceApi(BASE_URL)

export const recoverAiConfigFromHistory = (data, config) => {
  return $httpPost(`${BASE_URL}/recover`, data, config)
}

export const loadHistoryDiff = (data, config) => {
  return $httpPost(`${BASE_URL}/loadHistoryDiff`, data, config)
}

export const searchHistories = (id, data, config) => {
  return $httpPost(`${BASE_URL}/histories/${id}`, data, config)
}

export const testAiConfig = (idOrConfig, data, config) => {
  if (typeof idOrConfig === 'number') {
    return $httpPost(`${BASE_URL}/${idOrConfig}/test`, data, config)
  }
  return $httpPost(`${BASE_URL}/test`, { ...data, config: idOrConfig }, config)
}

export const loadAiModels = (idOrConfig, config) => {
  if (typeof idOrConfig === 'number') {
    return $httpGet(`${BASE_URL}/${idOrConfig}/models`, config)
  }
  return $httpPost(`${BASE_URL}/models`, idOrConfig, config)
}
