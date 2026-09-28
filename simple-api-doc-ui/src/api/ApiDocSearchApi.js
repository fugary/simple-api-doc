import { $http } from '@/vendors/axios'
import { getShareConfig } from '@/api/SimpleShareApi'

export const searchDocs = (data, shareId) => {
  return $http({
    ...(shareId ? getShareConfig(shareId) : {}),
    url: shareId ? `/shares/advancedSearch/${shareId}` : '/admin/docs/advancedSearch',
    method: 'post',
    data,
    loading: false
  }).then(response => response.data)
}

export const searchDocProjects = (docName) => {
  return $http({
    url: '/admin/docs/searchProjects',
    method: 'post',
    data: { docName, page: { pageNumber: 1, pageSize: 20 } },
    loading: false
  }).then(response => response.data?.resultData || [])
}
