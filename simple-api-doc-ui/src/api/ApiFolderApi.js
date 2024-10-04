import { useResourceApi } from '@/hooks/ApiHooks'
import { useDefaultPage } from '@/config'

const BASE_URL = '/admin/folders'

const ApiFolderApi = useResourceApi(BASE_URL)

export const loadAvailableFolders = (projectId) => {
  return ApiFolderApi.search({ projectId, page: useDefaultPage(200) })
    .then(data => data?.resultData?.filter(folder => folder.enabled) || [])
}

export default ApiFolderApi
