import { isFunction } from 'lodash-es'
import { DynamicHelper } from '@/components/directives'
import { h, defineComponent, defineAsyncComponent } from 'vue'
import ApiFolderEditWindow from '@/views/components/api/doc/comp/ApiFolderEditWindow.vue'

const ApiRequestPreviewWindow = () => import('@/views/components/api/ApiRequestPreviewWindow.vue')
const ApiEnvContentWindow = () => import('@/views/components/api/project/ApiEnvContentWindow.vue')
const ApiEnvParams = () => import('@/views/components/api/ApiEnvParams.vue')
const ShowUserInfo = () => import('@/views/components/user/ShowUserInfo.vue')
const JsonSchemaEdit = () => import('@/views/components/api/project/schema/JsonSchemaEdit.vue')
const ApiProjectComponentWindow = () => import('@/views/components/api/project/ApiProjectComponentWindow.vue')
const ApiDocRequestParamsEditWindow = () => import('@/views/components/api/doc/comp/edit/ApiDocRequestParamsEditWindow.vue')
const SecurityRequirementsWindow = () => import('@/views/components/api/doc/comp/edit/SecurityRequirementsWindow.vue')
const ApiHistoryListWindow = () => import('@/views/components/api/doc/comp/ApiHistoryListWindow.vue')
const CodeWindow = () => import('@/views/components/utils/CodeWindow.vue')
const MarkdownWindow = () => import('@/views/components/utils/MarkdownWindow.vue')
const SimpleJsonDataWindow = () => import('@/views/components/utils/SimpleJsonDataWindow.vue')

export const closeAllOnRouteChange = () => {
  document.querySelectorAll('.el-overlay:not([style*="display: none"]) .common-window .el-dialog__headerbtn:not(.dialog-fullscreen-btn)')
    .forEach(target => target?.click())
}

export const showUserInfo = async (id) => {
  const dynamicHelper = new DynamicHelper()
  const vnode = await dynamicHelper.createAndRender(ShowUserInfo, {
    onClosed: () => dynamicHelper.destroy()
  })
  vnode.component?.exposed?.showUserInfo(id)
}

export const previewApiRequest = async (...args) => {
  const dynamicHelper = new DynamicHelper()
  const vnode = await dynamicHelper.createAndRender(ApiRequestPreviewWindow, {
    onClosed: () => dynamicHelper.destroy()
  })
  vnode.component?.exposed?.toPreviewRequest(...args)
}

export const toEditGroupEnvParams = async (...args) => {
  const dynamicHelper = new DynamicHelper()
  const vnode = await dynamicHelper.createAndRender(ApiEnvParams, {
    onClosed: () => dynamicHelper.destroy()
  })
  return vnode.component?.exposed?.toEditGroupEnvParams(...args)
}

export const toEditEnvConfigs = async (...args) => {
  const dynamicHelper = new DynamicHelper()
  const vnode = await dynamicHelper.createAndRender(ApiEnvContentWindow, {
    onClosed: () => dynamicHelper.destroy()
  })
  return vnode.component?.exposed?.toEditEnvConfigs(...args)
}

export const addOrEditFolderWindow = async (id, projectId, parentFolder, config) => {
  const dynamicHelper = new DynamicHelper()
  const vnode = await dynamicHelper.createAndRender(ApiFolderEditWindow, {
    onClosed: () => dynamicHelper.destroy(),
    ...config
  })
  return vnode.component?.exposed?.addOrEditFolderWindow(id, projectId, parentFolder)
}

export const toEditJsonSchema = async (schemaData, currentInfoDetail, config) => {
  const dynamicHelper = new DynamicHelper()
  const vnode = await dynamicHelper.createAndRender(JsonSchemaEdit, {
    onClosed: () => dynamicHelper.destroy(),
    ...config
  })
  return vnode.component?.exposed?.toEditJsonSchema(schemaData, currentInfoDetail)
}

export const toEditComponent = async (currentInfoDetail, config) => {
  const dynamicHelper = new DynamicHelper()
  const vnode = await dynamicHelper.createAndRender(ApiProjectComponentWindow, {
    onClosed: () => dynamicHelper.destroy(),
    ...config
  })
  return vnode.component?.exposed?.toEditComponent(currentInfoDetail)
}

/**
 * @param code String 代码内容
 * @param config {CodeWindowConfig} 配置信息
 */
export const showCodeWindow = async (code, config = {}) => {
  const dynamicHelper = new DynamicHelper()
  const vnode = await dynamicHelper.createAndRender(CodeWindow, {
    onClosed: () => dynamicHelper.destroy()
  })
  vnode.component?.exposed?.showCodeWindow(code, config)
}

export const showMarkdownWindow = async (contentOrConfig, config = {}) => {
  const dynamicHelper = new DynamicHelper()
  const vnode = await dynamicHelper.createAndRender(MarkdownWindow, {
    onClosed: () => dynamicHelper.destroy(),
    ...config
  })
  vnode.component?.exposed?.showMarkdownWindow(contentOrConfig)
}

export const toEditApiDocRequestParams = async (docDetail, config = {}) => {
  const dynamicHelper = new DynamicHelper()
  const vnode = await dynamicHelper.createAndRender(ApiDocRequestParamsEditWindow, {
    onClosed: () => dynamicHelper.destroy(),
    ...config
  })
  vnode.component?.exposed?.toEditApiDocRequestParams(docDetail)
}

export const toEditSecuritySchemas = async (config = {}) => {
  const dynamicHelper = new DynamicHelper()
  const vnode = await dynamicHelper.createAndRender(SecurityRequirementsWindow, {
    onClosed: () => dynamicHelper.destroy(),
    ...config
  })
  vnode.component?.exposed?.toEditSecuritySchemas()
}

export const showHistoryListWindow = async (config) => {
  const dynamicHelper = new DynamicHelper()
  const vnode = await dynamicHelper.createAndRender(ApiHistoryListWindow, {
    onClosed: () => dynamicHelper.destroy(),
    ...config
  })
  vnode.component?.exposed?.showHistoryListWindow()
}

export const showJsonDataWindow = async (...args) => {
  const dynamicHelper = new DynamicHelper()
  const vnode = await dynamicHelper.createAndRender(SimpleJsonDataWindow, {
    onClosed: () => dynamicHelper.destroy()
  })
  vnode.component?.exposed?.showJsonDataWindow(...args)
}

/**
 * 构建新名字的组件，用于keepalive缓存
 *
 * @param {String} name 组件自定义名称
 * @param {Component | Promise<Component>} component 组件或者()=>import(组件)
 * @return {Component}
 */
export function createNewComponent (name, component) {
  return defineComponent({
    name,
    setup () {
      const oldComponent = isFunction(component) ? defineAsyncComponent(component) : component
      return () => h(oldComponent)
    }
  })
}

export default {
  /**
     * 动态组件工具
     * @param app {import('vue').App}
     */
  install (app) {
    DynamicHelper.app = app
  }
}
