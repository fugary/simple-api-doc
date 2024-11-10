import { useResourceApi } from '@/hooks/ApiHooks'
import { useDefaultPage } from '@/config'
import { $httpPost } from '@/vendors/axios'

const BASE_URL = '/admin/folders'

const ApiFolderApi = useResourceApi(BASE_URL)

export const loadAvailableFolders = (projectId) => {
  return ApiFolderApi.search({ projectId, page: useDefaultPage(200) })
    .then(data => data?.resultData?.filter(folder => folder.enabled) || [])
}

export const updateFolderSorts = (param, config) => {
  return $httpPost(`${BASE_URL}/updateSorts`, param, config)
}

export default ApiFolderApi
