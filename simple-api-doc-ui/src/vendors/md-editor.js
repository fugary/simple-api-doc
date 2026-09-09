import { MdPreview, MdEditor, config } from 'md-editor-v3'
import mermaid from 'mermaid'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { BASE_URL } from '@/config'

/**
 * Markdown 代码块自动折叠行数阈值（默认 30 行容易收起常规 DTO 与类声明代码，统一调大至 200 行）
 */
export const MD_AUTO_FOLD_THRESHOLD = 200

export const initMdPropsDefault = () => {
  if (MdPreview?.props?.autoFoldThreshold) {
    MdPreview.props.autoFoldThreshold.default = MD_AUTO_FOLD_THRESHOLD
  }
  if (MdEditor?.props?.autoFoldThreshold) {
    MdEditor.props.autoFoldThreshold.default = MD_AUTO_FOLD_THRESHOLD
  }
}
initMdPropsDefault()

/**
 * 新增一个markdown-it的插件，处理上传文件相对路径，方便在不同环境中展示
 * @param md
 */
const imagePathTransformPlugin = (md) => {
  const imageRule = md.renderer.rules.image
  if (imageRule != null) {
    md.renderer.rules.image = (tokens, idx, options, env, self) => {
      const token = tokens[idx]
      const src = token.attrGet('src')
      if (src && !src.match(/https?:\/\/.*/)) { // 相对路径添加BASE_URL信息
        console.log('===================================image', src, BASE_URL + src)
        token.attrSet('src', BASE_URL + src)
      }
      return imageRule(tokens, idx, options, env, self)
    }
  }
}

export const initEditorLink = () => {
  document.addEventListener('click', (event) => {
    const link = event.target.closest('.md-doc-container a')
    if (link) {
      const href = link.getAttribute('href')
      if (href?.startsWith('#')) {
        event.preventDefault()
        try {
          const targetId = decodeURIComponent(href.substring(1))
          const targetEl = document.getElementById(targetId) || document.getElementById(encodeURIComponent(targetId))
          targetEl?.scrollIntoView({ behavior: 'smooth' })
        } catch {
          // ignore
        }
      }
    }
  })
}

export default {
  install () {
    initMdPropsDefault()
    // md-editor-v3 提供本地 instance 时会跳过 mermaid.initialize() 的初始调用（onMounted 直接 return），
    // 需手动初始化一次；读 GlobalConfigStore 确保与应用主题状态一致。
    const { isDarkTheme } = useGlobalConfigStore()
    mermaid.initialize({
      startOnLoad: false,
      theme: isDarkTheme ? 'dark' : 'default'
    })
    config({
      editorExtensions: {
        mermaid: {
          instance: mermaid
        }
      },
      markdownItConfig (mdit) {
        mdit.use(imagePathTransformPlugin)
      }
    })
    initEditorLink()
  }
}
