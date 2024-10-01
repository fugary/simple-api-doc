import { useResourceApi } from '@/hooks/ApiHooks'

const BASE_URL = '/admin/shares'

const ApiProjectShareApi = useResourceApi(BASE_URL)

export default ApiProjectShareApi
