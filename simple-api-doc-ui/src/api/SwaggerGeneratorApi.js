import axios from 'axios'
import { SIMPLE_API_ACCESS_TOKEN_HEADER, SIMPLE_API_TARGET_URL_HEADER } from '@/consts/ApiConstants'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import { useLoginConfigStore } from '@/stores/LoginConfigStore'
import { $httpGet, $httpPost } from '@/vendors/axios'
import { $downloadWithLinkClick } from '@/utils'
import { getShareConfig } from '@/api/SimpleShareApi'

const GEN_BASE_URL = import.meta.env.VITE_APP_SWAGGER_GENERATOR_URL

const $http = axios.create({
  baseURL: GEN_BASE_URL,
  timeout: import.meta.env.VITE_APP_API_TIMEOUT
})

export const GEN_CLIENTS_URL = '/gen/clients'
/**
 * 加载支持的语言
 * @param [param] {{useProxy:Boolean, shareDoc: {shareId: String}}} 参数
 * @param config
 * @returns {Promise<*>}
 */
export const loadClientLanguages = (param, config = {}) => {
  if (!param?.useProxy) {
    let url = `/admin${GEN_CLIENTS_URL}`
    if (param?.shareDoc) {
      config = Object.assign(getShareConfig(param.shareDoc.shareId), config || {})
      url = `/shares${GEN_CLIENTS_URL}`
    }
    return $httpGet(url, config)
  }
  return $http({ url: GEN_CLIENTS_URL, method: 'get' }, config)
    .then(response => response.data)
}
/**
 * 加载配置信息
 * @param language
 * @param [param] {{useProxy:Boolean, shareDoc: {shareId: String}}} 参数
 * @param config
 * @returns {Promise<axios.AxiosResponse<any>>}
 */
export const loadLanguageConfig = (language, param, config = {}) => {
  if (!param?.useProxy) {
    let url = `/admin${GEN_CLIENTS_URL}/${language}`
    if (param?.shareDoc) {
      config = Object.assign(getShareConfig(param.shareDoc.shareId), config || {})
      url = `/shares${GEN_CLIENTS_URL}/${language}`
    }
    return $httpGet(url, config)
  }
  return $http({ url: `${GEN_CLIENTS_URL}/${language}`, method: 'get' }, config)
    .then(response => response.data)
}

export const newGenerateCode = (param, config) => {
  let proxy = ''
  let headers = {}
  if (param.useProxy) {
    proxy = '/proxy'
    headers = {
      [SIMPLE_API_TARGET_URL_HEADER]: GEN_BASE_URL
    }
  }
  let targetUrl = `/admin${proxy}${GEN_CLIENTS_URL}/${param.language}` // 服务端代理发送
  headers[SIMPLE_API_ACCESS_TOKEN_HEADER] = useLoginConfigStore().accessToken
  if (param.shareDoc) {
    targetUrl = `/shares${proxy}${GEN_CLIENTS_URL}/${param.language}`
    headers[SIMPLE_API_ACCESS_TOKEN_HEADER] = useShareConfigStore().getShareToken(param.shareDoc.shareId)
  }
  return $httpPost(targetUrl, param.data, Object.assign({ headers }, config))
}

export const downloadGeneratedUrl = (url, { useProxy, shareDoc }) => {
  if (!useProxy) {
    let accessToken = useLoginConfigStore().accessToken
    if (shareDoc && shareDoc.shareId) {
      accessToken = useShareConfigStore().getShareToken(shareDoc.shareId)
    }
    url = `${url}?access_token=${accessToken}`
  }
  $downloadWithLinkClick(url)
}
