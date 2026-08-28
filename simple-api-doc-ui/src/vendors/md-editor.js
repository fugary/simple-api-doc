import { config } from 'md-editor-v3'
import { BASE_URL } from '@/config'

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
    config({
      markdownItConfig (mdit) {
        mdit.use(imagePathTransformPlugin)
      }
    })
    initEditorLink()
  }
}
