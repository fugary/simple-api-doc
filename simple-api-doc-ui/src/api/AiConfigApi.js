import { $httpPost } from '@/vendors/axios'
import { useResourceApi } from '@/hooks/ApiHooks'

export const AiConfigApi = useResourceApi('/admin/ai/configs')

export const recoverAiConfigFromHistory = (data, config) => {
  return $httpPost('/admin/ai/configs/recover', data, config)
}

export const loadHistoryDiff = (data, config) => {
  return $httpPost('/admin/ai/configs/loadHistoryDiff', data, config)
}

export const searchHistories = (id, data, config) => {
  return $httpPost(`/admin/ai/configs/histories/${id}`, data, config)
}
