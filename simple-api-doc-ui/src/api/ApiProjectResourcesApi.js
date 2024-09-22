import { useResourceApi } from '@/hooks/ApiHooks'

const API_PROJECT_URL = '/admin/resources'

const ApiProjectResourceApi = useResourceApi(API_PROJECT_URL)

export default ApiProjectResourceApi
