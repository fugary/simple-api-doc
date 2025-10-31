import { useResourceApi } from '@/hooks/ApiHooks'

const BASE_URL = '/admin/dbs'

const ApiDbApi = useResourceApi(BASE_URL)

export default ApiDbApi
