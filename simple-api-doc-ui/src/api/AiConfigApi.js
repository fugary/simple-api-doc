import { $httpPost } from '@/vendors/axios'
import { useResourceApi } from '@/hooks/ApiHooks'

export const AiConfigApi = useResourceApi('/admin/ai/configs')

export const recoverAiConfigFromHistory = (data, config) => {
  return $httpPost('/admin/ai/configs/recover', data, config)
}
