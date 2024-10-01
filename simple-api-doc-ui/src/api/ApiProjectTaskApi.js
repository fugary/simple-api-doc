import { useResourceApi } from '@/hooks/ApiHooks'

const BASE_URL = '/admin/tasks'

const ApiProjectTaskApi = useResourceApi(BASE_URL)

export default ApiProjectTaskApi
