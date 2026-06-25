import { $httpPost, $httpGet } from '@/vendors/axios'

export const aiGenerateSample = (params, config) => {
  return $httpPost('/api/ai/generate-sample', params, config)
}

export const getAiStatus = (config) => {
  return $httpGet('/api/ai/status', null, config)
}
