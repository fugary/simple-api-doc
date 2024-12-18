import axios from 'axios'
import { SIMPLE_API_ACCESS_TOKEN_HEADER, SIMPLE_API_TARGET_URL_HEADER } from '@/consts/ApiConstants'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import { useLoginConfigStore } from '@/stores/LoginConfigStore'
import { $httpPost } from '@/vendors/axios'

const GEN_BASE_URL = import.meta.env.VITE_APP_SWAGGER_GENERATOR_URL

const $http = axios.create({
  baseURL: GEN_BASE_URL,
  timeout: import.meta.env.VITE_APP_API_TIMEOUT
})

export const GEN_CLIENTS_URL = '/gen/clients'
/**
 * 加载支持的语言
 * @param config
 * @returns {Promise<*>}
 */
export const loadClientLanguages = (config = {}) => {
  return $http({ url: GEN_CLIENTS_URL, method: 'get' }, config)
    .then(response => response.data)
}
/**
 * 加载配置信息
 * @param language
 * @param config
 * @returns {Promise<axios.AxiosResponse<any>>}
 */
export const loadLanguageConfig = (language, config = {}) => {
  return $http({ url: `${GEN_CLIENTS_URL}/${language}`, method: 'get' }, config)
    .then(response => response.data)
}
/**
 * 生成代码
 * @param language
 * @param data
 * @param config
 * @returns {Promise<axios.AxiosResponse<any>>}
 */
export const generateCode = (language, data, config = {}) => {
  return $http({
    url: `${GEN_CLIENTS_URL}/${language}`, data, method: 'post'
  }, config).then(response => response.data)
}

export const newGenerateCode = (param, config) => {
  let targetUrl = `/admin/proxy${GEN_CLIENTS_URL}/${param.language}` // 服务端代理发送
  const headers = {
    [SIMPLE_API_TARGET_URL_HEADER]: GEN_BASE_URL
  }
  headers[SIMPLE_API_ACCESS_TOKEN_HEADER] = useLoginConfigStore().accessToken
  if (param.shareDoc) {
    targetUrl = `/shares/proxy${GEN_CLIENTS_URL}/${param.language}`
    headers[SIMPLE_API_ACCESS_TOKEN_HEADER] = useShareConfigStore().getShareToken(param.shareDoc.shareId)
  }
  return $httpPost(targetUrl, param.data, Object.assign({ headers }, config))
}
