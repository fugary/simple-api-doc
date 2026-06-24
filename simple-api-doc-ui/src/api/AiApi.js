import { $httpPost } from '@/vendors/axios'

export const aiGenerateSample = (params, config) => {
  return $httpPost('/api/ai/generate-sample', params, config)
}
