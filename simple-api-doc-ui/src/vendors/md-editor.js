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

export default {
  install () {
    config({
      markdownItConfig (mdit) {
        mdit.use(imagePathTransformPlugin)
      }
    })
  }
}
