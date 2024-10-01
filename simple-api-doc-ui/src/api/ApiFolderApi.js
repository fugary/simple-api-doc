import { useResourceApi } from '@/hooks/ApiHooks'

const BASE_URL = '/admin/folders'

const ApiFolderApi = useResourceApi(BASE_URL)

export default ApiFolderApi
