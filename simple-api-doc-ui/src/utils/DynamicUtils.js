import { isFunction } from 'lodash-es'
import { DynamicHelper } from '@/components/directives'
import { h, defineComponent, defineAsyncComponent } from 'vue'

const ApiRequestPreviewWindow = () => import('@/views/components/api/ApiRequestPreviewWindow.vue')
const ApiEnvParams = () => import('@/views/components/api/ApiEnvParams.vue')
const ShowUserInfo = () => import('@/views/components/user/ShowUserInfo.vue')
const CodeWindow = () => import('@/views/components/utils/CodeWindow.vue')
const ApiDocExportWindow = () => import('@/views/components/api/doc/comp/ApiDocExportWindow.vue')

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

export const toExportApiDocs = async (...args) => {
  const dynamicHelper = new DynamicHelper()
  const vnode = await dynamicHelper.createAndRender(ApiDocExportWindow, {
    onClosed: () => dynamicHelper.destroy()
  })
  vnode.component?.exposed?.toExportApiDocs(...args)
}

export const toEditGroupEnvParams = async (...args) => {
  const dynamicHelper = new DynamicHelper()
  const vnode = await dynamicHelper.createAndRender(ApiEnvParams, {
    onClosed: () => dynamicHelper.destroy()
  })
  return vnode.component?.exposed?.toEditGroupEnvParams(...args)
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
