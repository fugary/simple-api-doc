import { useResourceApi } from '@/hooks/ApiHooks'

const BASE_URL = '/admin/ai/caches'

const AiCacheApi = useResourceApi(BASE_URL)

export default AiCacheApi
