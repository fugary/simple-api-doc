import { $httpPost, $httpGet } from '@/vendors/axios'
import { getShareConfig } from '@/api/SimpleShareApi'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import { useResourceApi } from '@/hooks/ApiHooks'
const getAiConfig = (config = {}) => {
  console.log('===============================config', config)
  const { preferenceId, ...restConfig } = config
  const isShare = preferenceId && useShareConfigStore().getShareToken(preferenceId)
  const baseUrl = isShare ? '/shares/ai' : '/admin/ai'
  const finalConfig = isShare ? Object.assign(getShareConfig(preferenceId), restConfig) : restConfig
  return { baseUrl, finalConfig }
}

export const aiGenerateSample = (params, config) => {
  const { baseUrl, finalConfig } = getAiConfig(config)
  return $httpPost(`${baseUrl}/generate-sample`, params, finalConfig)
}

export const getAiStatus = (config) => {
  const { baseUrl, finalConfig } = getAiConfig(config)
  return $httpGet(`${baseUrl}/status`, finalConfig)
}

export const AiCacheApi = useResourceApi('/admin/ai/caches')
