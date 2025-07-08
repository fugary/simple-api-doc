import { useResourceApi } from '@/hooks/ApiHooks'

const BASE_URL = '/admin/info/detail'

const ApiProjectInfoDetailApi = useResourceApi(BASE_URL)

export default ApiProjectInfoDetailApi
