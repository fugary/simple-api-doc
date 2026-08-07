import { $httpGet } from '@/vendors/axios'

/**
 * 获取 dashboard 数据指标
 */
export const getMetrics = (all) => $httpGet('/admin/dashboard/metrics', { params: { all } })

/**
 * 获取最近趋势
 */
export const getTrend = (all, days = 30) => $httpGet('/admin/dashboard/trend', { params: { all, days } })

/**
 * 获取最近修改的项目
 */
export const getRecentProjects = (all) => $httpGet('/admin/dashboard/recentProjects', { params: { all } })

/**
 * 获取最近的分享
 */
export const getRecentShares = (all) => $httpGet('/admin/dashboard/recentShares', { params: { all } })

/**
 * 获取最近导入
 */
export const getRecentImports = (all) => $httpGet('/admin/dashboard/recentImports', { params: { all } })
