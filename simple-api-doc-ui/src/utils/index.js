import dayjs from 'dayjs'
import { markRaw, ref } from 'vue'
import { isObject, isArray, set, isNumber, isFunction, cloneDeep } from 'lodash-es'
import { ElLoading, ElMessageBox, ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import numeral from 'numeral'
import { useClipboard } from '@vueuse/core'
import { LOADING_DELAY, SYSTEM_KEY, REMEMBER_SEARCH_PARAM_ENABLED } from '@/config'
import { $i18nBundle } from '@/messages'
import { useRoute } from 'vue-router'
import { useLoginConfigStore } from '@/stores/LoginConfigStore'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { useGlobalSearchParamStore } from '@/stores/GlobalSearchParamStore'
import { useTabsViewStore } from '@/stores/TabsViewStore'
import { GlobalLayoutMode, LoadSaveParamMode } from '@/consts/GlobalConstants'

/**
 * 地址信息
 * @param path
 * @return {`${string}${string}`}
 */
export const getPathUrl = (path) => {
  if (/^https?:\/\//.test(path)) {
    return path
  }
  const baseUrl = location.origin + location.pathname
  return `${baseUrl}#${path}`
}

export const isAdminUser = () => {
  return isUserAdmin(useLoginConfigStore().accountInfo?.userName)
}

export const useCurrentUserName = () => {
  return useLoginConfigStore().accountInfo?.userName
}

export const isUserAdmin = userName => {
  return userName === 'admin'
}

export const useSystemKey = () => {
  return SYSTEM_KEY
}

export const formatDate = (date, format) => {
  if (date) {
    return dayjs(date).format(format || 'YYYY-MM-DD HH:mm:ss')
  }
  return ''
}

export const formatDay = (date, format) => {
  if (date) {
    return dayjs(date).format(format || 'YYYY-MM-DD')
  }
  return ''
}
/**
 * 数组转换成 key:value的对象，可以为a.b.c格式
 * @param item
 * @param parentKey
 */
export const toFlatKeyValue = (item, parentKey = '') => {
  const result = {}
  for (const key in item) {
    if (key === '@class') { // 过滤掉一些不用的属性
      continue
    }
    const newKey = parentKey ? `${parentKey}.${key}` : key
    if (isObject(item[key]) && !isArray(item[key])) {
      Object.assign(result, toFlatKeyValue(item[key], newKey))
    } else {
      result[newKey] = item[key]
    }
  }
  return result
}
/**
 * 转换a.b.c形式为对象格式
 * @param item
 */
export const toKeyValueObj = (item) => {
  const result = {}
  for (const objKey in item) {
    set(result, objKey, item[objKey])
  }
  return result
}
/**
 * 转换成get参数
 * @param obj
 * @return {string}
 */
export const toGetParams = (obj) => {
  if (isObject(obj)) {
    obj = toFlatKeyValue(obj)
    return Object.keys(obj)
      .filter(key => !!obj[key])
      .map(key => `${key}=${obj[key]}`).join('&')
  }
}
/**
 * 获取GET参数为对象
 * @param queryStr
 * @return {{[p: string]: string}}
 */
export const fromGetParams = (queryStr) => {
  return Object.fromEntries(new URLSearchParams(queryStr).entries())
}
/**
 * 连接path路径
 * @param baseUrl
 * @param path
 * @return {`/${string|string}`}
 */
export const joinPath = (baseUrl = '', path = '') => {
  while (baseUrl.endsWith('/')) {
    baseUrl = baseUrl.substring(0, baseUrl.length - 1)
  }
  path = path.startsWith('/') ? path.substring(1) : path
  return `${baseUrl}/${path}`
}

/**
 * 给指定URL增加GET参数
 * @param url {String} url地址
 * @param params {Object} 参数
 * @return {string} 返回新URL
 */
export const addParamsToURL = (url, params = {}) => {
  const queryIndex = url.indexOf('?')
  const hasParams = queryIndex > -1
  const getParams = hasParams ? fromGetParams(url.substring(queryIndex + 1)) : {}
  const baseUrl = hasParams ? url.substring(0, queryIndex) : url
  return `${baseUrl}?${toGetParams({ ...getParams, ...params })}`
}

/**
 * 字符串数组转换成选项
 */
export const getSingleSelectOptions = (...dataList) => {
  return (dataList || []).map(data => {
    return {
      label: `${data}`,
      value: data
    }
  })
}

let router = null
/**
 * @param {string|RouteLocationRaw|number} path 路径、路由对象、数字
 * @param replace 是否用replace方法
 * @return {*|Promise<T>}
 */
export const $goto = (path, replace = false) => {
  path = path || -1
  if (isNumber(path)) {
    return Promise.resolve().then(() => router?.go(path))
  } else {
    if (replace) {
      return router?.replace(path)
    } else {
      return router?.push(path)
    }
  }
}
// 定制窗口打开模式
const oriOpen = window.open
/**
 * 通用打开窗口逻辑，统一处理不同模式下窗口打开
 * @param path 路径：/xxx/xxx1
 * @param target 目标窗口，仅窗口有效，_blank默认, _self
 * @param forceNewWin 是否强制新窗口打开
 */
export const $openWin = (path, target = '_blank', forceNewWin = false) => {
  const tabsViewStore = useTabsViewStore()
  if (path) { // path有值才跳转
    if (path.match(/^\w+?:\/\/.+/)) { // http等协议不拦截跳转逻辑
      oriOpen(path, target || '_blank')
      return
    }
    const hasHash = path.startsWith('#')
    if (tabsViewStore.isTabMode && !forceNewWin) {
      path = hasHash ? path.substring(1) : path
      $goto(path)
    } else {
      path = hasHash ? path : `#${path}`
      oriOpen(path, target || '_blank')
    }
  }
}
window.open = $openWin

export const $openNewWin = path => $openWin(path, '_blank', true)

/**
 * 统一处理一些backUrl信息
 * @param [defaultUrl] 默认返回路径
 * @return {{backUrl: Ref<String>, setBackUrl: (backUrl:String)=>void, goBack: () => void}
 */
export const useBackUrl = (defaultUrl) => {
  const backUrl = ref(defaultUrl)
  const setBackUrl = (url) => {
    backUrl.value = url
  }
  const goBack = () => {
    console.info('===================================goback', backUrl.value, window.history)
    if (backUrl.value) {
      if (REMEMBER_SEARCH_PARAM_ENABLED && useGlobalConfigStore().loadSaveParamMode === LoadSaveParamMode.BACK) {
        useGlobalSearchParamStore().setSaveParamBack(true)
      }
      $goto(backUrl.value, true)
    } else if (window.history.length > 1) {
      window.history.go(-1)
    } else {
      window.close()
    }
  }
  const route = useRoute()
  if (route?.query?.backUrl) {
    setBackUrl(route.query.backUrl)
  }
  return {
    backUrl,
    setBackUrl,
    goBack
  }
}

/**
 * @param {RouteLocationNormalizedLoaded} route 路由，不可为空
 */
export const $reload = (route) => {
  const currentRoute = route
  if (currentRoute) {
    const time = new Date().getTime()
    const query = Object.assign({}, currentRoute.query, { _t: time })
    $goto(`${currentRoute.path}?${toGetParams(query)}`, true)
  }
}

export const useReload = () => {
  const route = useRoute()
  return {
    reload: () => {
      $reload(route)
    }
  }
}

export const $logout = () => {
  useLoginConfigStore().logout()
  Promise.resolve().then(() => {
    $goto('/login')
  })
}
const globalLoadingConfig = {
  delay: LOADING_DELAY,
  globalLoading: null,
  delayLoadingId: null
}

export const $coreShowLoading = (message, config = {}) => {
  if (isObject(message)) { // 第一个参数就是对象，表示是配置对象
    config = message
  } else {
    config.text = message
  }
  const globalLoading = globalLoadingConfig.globalLoading
  if (globalLoading) {
    globalLoading.close()
  }
  const openLoading = () => ElLoading.service(Object.assign({
    lock: true,
    background: 'rgba(0, 0, 0, 0.7)'
  }, config))
  const delay = config.delay ?? globalLoadingConfig.delay
  if (delay > 0) {
    globalLoadingConfig.delayLoadingId = setTimeout(() => {
      globalLoadingConfig.globalLoading = openLoading()
    }, globalLoadingConfig.delay)
  } else {
    globalLoadingConfig.globalLoading = openLoading()
  }
}

export const $coreHideLoading = () => {
  globalLoadingConfig.delayLoadingId && clearTimeout(globalLoadingConfig.delayLoadingId)
  globalLoadingConfig.delayLoadingId = null
  globalLoadingConfig.globalLoading?.close()
}

export const $coreAlert = (message, title = $i18nBundle('common.label.reminder'), options = undefined) => {
  if (isObject(title) && !options) {
    options = title
    title = null
  }
  options = Object.assign({
    type: 'info',
    dangerouslyUseHTMLString: true,
    draggable: true,
    customClass: 'common-message-alert'
  }, options || {})
  return ElMessageBox.alert(message,
    title || $i18nBundle('common.label.reminder'),
    options)
}

export const $coreError = (message, title = $i18nBundle('common.label.reminder'), options = undefined) => {
  return $coreAlert(message, title, Object.assign({
    type: 'error'
  }, options || {}))
}

export const $coreWarning = (message, title = $i18nBundle('common.label.reminder'), options = undefined) => {
  return $coreAlert(message, title, Object.assign({
    type: 'warning'
  }, options || {}))
}

export const $coreSuccess = (message, title = $i18nBundle('common.label.reminder'), options = undefined) => {
  return $coreAlert(message, title, Object.assign({
    type: 'success'
  }, options || {}))
}

export const $coreConfirm = (message, title = $i18nBundle('common.label.reminder'), options = undefined) => {
  if (isObject(title) && !options) {
    options = title
    title = null
  }
  options = Object.assign({
    icon: markRaw(QuestionFilled),
    dangerouslyUseHTMLString: true,
    draggable: true,
    customClass: 'common-message-confirm'
  }, options || {})
  return ElMessageBox.confirm(message,
    title || $i18nBundle('common.label.reminder'),
    options)
}

export const $formatNumber = (value, format) => {
  return numeral(value).format(format)
}

export const $number = (value, size) => {
  const digits = []
  for (let i = 0; i < size; i++) {
    digits.push('0')
  }
  return $formatNumber(value, '0,0.' + digits.join(''))
}

export const $currency = (value, prefix) => {
  return `${prefix || '¥'} ${$formatNumber(value, '0,0.00')}`
}

export const $currencyShort = (value, prefix) => {
  return `${prefix || '¥'} ${$formatNumber(value, '0,0.[00]')}`
}
/**
 * @typedef {{text:string, success?:string, error?:string}} CopyTextConfig
 * @type {CopyTextConfig}
 */
const defaultCopyConfig = {
  success: 'Copied Successfully!',
  error: 'Copy Not supported!'
}
/**
 * @param text {string | CopyTextConfig} 需要复制的文本
 * @return void
 */
export const $copyText = (text) => {
  if (text) {
    let config
    if (isObject(text)) {
      config = { ...defaultCopyConfig, ...text }
    } else {
      config = { ...defaultCopyConfig, text }
    }
    if (config.text) {
      const { copy, isSupported } = useClipboard({ legacy: true })
      if (isSupported) {
        copy(config.text)
        ElMessage({
          message: config.success,
          type: 'success'
        })
      } else {
        ElMessage({
          message: config.error,
          type: 'error'
        })
      }
    }
  }
}

export const checkShowColumn = (dataList, field) => {
  let hasFieldData = false
  dataList = dataList || []
  let checkFun = (item, field) => isNumber(item[field]) || !!item[field]
  if (isFunction(field)) {
    checkFun = field
  }
  for (const item of dataList) {
    if (checkFun(item, field)) {
      hasFieldData = true
      break
    }
  }
  return hasFieldData
}

export const calcAffixOffset = (fix = 10) => {
  let initValue = 60 + fix
  useGlobalConfigStore().layoutMode === GlobalLayoutMode.TOP && useGlobalConfigStore().isShowBreadcrumb && (initValue += 40)
  useTabsViewStore().isTabMode && (initValue += 40)
  return initValue
}

/**
 * 列表数据解析成树结构
 * @template T
 * @param items {T[]}数据集
 * @param parent {T=} 上级，第一次调用都传null
 * @param config {Object=}配置信息
 * @param config.parentKey {string} 父级字段名
 * @param config.valueKey {string} 当前主字段名
 * @param config.pre {(item) => void} 前置处理，改变item的属性值
 * @param config.after {(item) => void} 后置处理，改变item的属性值
 * @returns {T[]}
 */
export const processTreeData = (items, parent, config) => {
  const results = []
  const { parentKey, valueKey, clone } = Object.assign({
    parentKey: 'parentId',
    valueKey: 'id',
    clone: true
  }, config || {})
  items.forEach(current => {
    if (!parent) {
      if (!current[parentKey]) { // 根节点
        const currentNode = clone ? cloneDeep(current) : current
        results.push(currentNode)
        isFunction(config?.pre) && config.pre(currentNode)
        processTreeData(items, currentNode, config)
        isFunction(config?.after) && config.after(currentNode)
      }
    } else {
      if (current[parentKey] === parent[valueKey]) {
        const currentNode = clone ? cloneDeep(current) : current
        parent.children = parent.children || []
        parent.children.push(currentNode)
        currentNode.parent = parent
        isFunction(config?.pre) && config.pre(currentNode)
        processTreeData(items, currentNode, config)
        isFunction(config?.after) && config.after(currentNode)
      }
    }
    return parent
  })
  return results
}
/**
 * 产生随机字符，数字加字母
 * @param len
 * @returns {string}
 */
export const $randomStr = (len = 8) => {
  const str = Math.random().toString(36).substring(2)
  if (str.length >= len) {
    return str.substring(0, len)
  }
  return str + $randomStr(len - str.length)
}
/**
 * 下载链接点击
 * @param downloadUrl
 */
export const $downloadWithLinkClick = (downloadUrl) => {
  const downloadLink = document.createElement('a')
  downloadLink.href = downloadUrl
  downloadLink.download = 'download'
  downloadLink.click()
}

export default {
  install (app) {
    router = app.config.globalProperties.$router
    Object.assign(app.config.globalProperties, {
      $goto,
      $reload,
      $back: $goto,
      $logout,
      $date: formatDate,
      $day: formatDay,
      $number,
      $currency,
      $currencyShort,
      $coreShowLoading,
      $coreHideLoading,
      $coreAlert,
      $coreSuccess,
      $coreWarning,
      $coreError,
      $coreConfirm,
      $openNewWin,
      $openWin,
      $randomStr
    })
  }
}
