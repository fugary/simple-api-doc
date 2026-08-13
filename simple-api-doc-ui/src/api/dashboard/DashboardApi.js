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

/**
 * 获取导入任务分布（手动 vs 定时）
 */
export const getImportTaskRatio = (all) => $httpGet('/admin/dashboard/importTaskRatio', { params: { all } })

/**
 * 获取项目分享数统计
 */
export const getProjectShareRatio = (all) => $httpGet('/admin/dashboard/projectShareRatio', { params: { all } })
