let pendingAnchor = null

/**
 * 设置待定位锚点
 * @param {string|null} anchor
 */
export const setPendingAnchor = (anchor) => {
  pendingAnchor = anchor || null
}

/**
 * 消费待定位锚点（获取并清空）
 * @returns {string|null}
 */
export const consumePendingAnchor = () => {
  const anchor = pendingAnchor
  pendingAnchor = null
  return anchor
}

/**
 * 判断是否为 UUID 字符串
 * @param {string} str
 * @returns {boolean}
 */
export const isUuid = (str) => {
  if (!str || typeof str !== 'string') return false
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(str.trim())
}

/**
 * 规范化文档路径（小写、统一斜杠、去除扩展名）
 * @param {string} p
 * @returns {string}
 */
export const normalizePath = (p) => {
  if (!p) return ''
  return p.replace(/\\/g, '/')
    .replace(/^\/+/, '')
    .replace(/\/+$/, '')
    .replace(/\.(md|markdown)$/i, '')
    .toLowerCase()
}

/**
 * 去除数字/序号前缀（如 01-guide -> guide, 01.start -> start）
 * @param {string} name
 * @returns {string}
 */
export const stripNumericPrefix = (name) => {
  if (!name) return ''
  return name.replace(/^(\d+)[.\-_ ]+/, '')
}

/**
 * 模糊规范化路径（去除数字序号与扩展名）
 * @param {string} p
 * @returns {string}
 */
export const normalizeFuzzy = (p) => {
  if (!p) return ''
  const segments = normalizePath(p).split('/')
  return segments.map(stripNumericPrefix).join('/')
}

/**
 * 获取路径中的文件名部分
 * @param {string} p
 * @returns {string}
 */
export const getFileName = (p) => {
  if (!p) return ''
  const norm = p.replace(/\\/g, '/').replace(/^\/+/, '').replace(/\/+$/, '')
  const lastSlash = norm.lastIndexOf('/')
  return lastSlash >= 0 ? norm.substring(lastSlash + 1) : norm
}

/**
 * 获取文档的祖先文件夹名称/标识列表
 * @param {Object} doc
 * @param {Array} folders
 * @returns {{folderNames: string[], folderCodes: string[]}}
 */
export const getDocAncestorFolderPaths = (doc, folders = []) => {
  const folderNames = []
  const folderCodes = []
  if (!doc) return { folderNames, folderCodes }

  if (doc.parent) {
    let curr = doc.parent
    const visited = new Set()
    while (curr && !visited.has(curr.id || curr.treeId)) {
      visited.add(curr.id || curr.treeId)
      if (!curr.rootFlag) {
        if (curr.folderName && curr.folderName !== '根目录') {
          folderNames.push(curr.folderName)
        }
        if (curr.folderCode && curr.folderCode !== 'root') {
          folderCodes.push(curr.folderCode)
        }
      }
      curr = curr.parent
    }
    folderNames.reverse()
    folderCodes.reverse()
    return { folderNames, folderCodes }
  }

  if (doc.folderId && folders && folders.length) {
    const folderMap = Object.fromEntries(folders.map(f => [f.id, f]))
    let currFolder = folderMap[doc.folderId]
    const visited = new Set()
    while (currFolder && !visited.has(currFolder.id)) {
      visited.add(currFolder.id)
      if (!currFolder.rootFlag) {
        if (currFolder.folderName && currFolder.folderName !== '根目录') {
          folderNames.push(currFolder.folderName)
        }
        if (currFolder.folderCode && currFolder.folderCode !== 'root') {
          folderCodes.push(currFolder.folderCode)
        }
      }
      currFolder = currFolder.parentId ? folderMap[currFolder.parentId] : currFolder.parent
    }
    folderNames.reverse()
    folderCodes.reverse()
  }

  return { folderNames, folderCodes }
}

/**
 * 获取文档的有效相对基准路径（优先使用非 UUID 的 docKey；否则由祖先文件夹 + docName/docKey 构建）
 * @param {Object} doc
 * @param {Array} folders
 * @returns {string}
 */
export const getDocEffectivePath = (doc, folders = []) => {
  if (!doc) return ''
  // 1. 如果 docKey 包含目录层级且非纯 UUID，直接使用
  if (doc.docKey && doc.docKey.includes('/') && !isUuid(doc.docKey)) {
    return doc.docKey.replace(/\\/g, '/').replace(/^\/+/, '')
  }

  // 2. 通过文件夹层级拼接逻辑路径
  const { folderNames, folderCodes } = getDocAncestorFolderPaths(doc, folders)
  const docName = doc.docName || (!isUuid(doc.docKey) ? doc.docKey : '') || 'doc.md'

  if (folderCodes.length > 0 && folderCodes.some(c => !isUuid(c))) {
    return `${folderCodes.join('/')}/${docName}`
  }
  if (folderNames.length > 0) {
    return `${folderNames.join('/')}/${docName}`
  }

  return docName
}

/**
 * 解析相对路径与锚点
 * @param {string|Object} currentDocOrKey 当前文档对象或其相对路径（如 "01-guide/start.md" 或 doc 对象）
 * @param {string} href 超链接 href（如 "../config/db.md#mysql"）
 * @param {Array} [folders] 可选的项目文件夹列表（用于计算手动创建文档的有效层级）
 * @returns {{targetPath: string, hash: string, isExternal: boolean, isAnchorOnly: boolean}}
 */
export const resolveRelativeDocPath = (currentDocOrKey = '', href = '', folders = []) => {
  if (!href || typeof href !== 'string') {
    return { targetPath: '', hash: '', isExternal: false, isAnchorOnly: false }
  }

  const trimmedHref = href.trim()
  // 外部链接判断
  if (/^(https?:|mailto:|tel:|\/\/)/i.test(trimmedHref)) {
    return { targetPath: trimmedHref, hash: '', isExternal: true, isAnchorOnly: false }
  }

  // 提取锚点 hash
  const hashIndex = trimmedHref.indexOf('#')
  const rawPath = hashIndex >= 0 ? trimmedHref.substring(0, hashIndex) : trimmedHref
  const rawHash = hashIndex >= 0 ? trimmedHref.substring(hashIndex + 1) : ''
  let decodedHash = ''
  try {
    decodedHash = decodeURIComponent(rawHash)
  } catch {
    decodedHash = rawHash
  }

  // 纯本页锚点
  if (!rawPath) {
    return { targetPath: '', hash: decodedHash, isExternal: false, isAnchorOnly: true }
  }

  let cleanPath = rawPath.replace(/\\/g, '/')
  try {
    cleanPath = decodeURIComponent(cleanPath)
  } catch {
    // 忽略解码异常
  }

  // 绝对路径（以 / 开头，基于项目根目录）
  if (cleanPath.startsWith('/')) {
    const targetPath = cleanPath.replace(/^\/+/, '')
    return { targetPath, hash: decodedHash, isExternal: false, isAnchorOnly: false }
  }

  // 计算当前文档基准路径
  let baseDocPath = ''
  if (typeof currentDocOrKey === 'object' && currentDocOrKey !== null) {
    baseDocPath = getDocEffectivePath(currentDocOrKey, folders)
  } else if (typeof currentDocOrKey === 'string') {
    baseDocPath = currentDocOrKey
  }

  const normalizedDocKey = (baseDocPath || '').replace(/\\/g, '/').replace(/^\/+/, '')
  const lastSlash = normalizedDocKey.lastIndexOf('/')
  const currentDir = lastSlash >= 0 ? normalizedDocKey.substring(0, lastSlash) : ''

  const baseSegments = currentDir ? currentDir.split('/') : []
  const hrefSegments = cleanPath.split('/')
  const resolved = [...baseSegments]

  for (const seg of hrefSegments) {
    if (!seg || seg === '.') continue
    if (seg === '..') {
      if (resolved.length > 0) {
        resolved.pop()
      }
    } else {
      resolved.push(seg)
    }
  }

  return {
    targetPath: resolved.join('/'),
    hash: decodedHash,
    isExternal: false,
    isAnchorOnly: false
  }
}

/**
 * 提取文档的所有可能识别路径/名称候选集
 * @param {Object} doc
 * @param {Array} folders
 * @returns {string[]}
 */
export const getDocCandidateKeys = (doc, folders = []) => {
  const candidates = new Set()
  if (!doc) return []

  // 1. 基础字段
  if (doc.docKey && !isUuid(doc.docKey)) {
    candidates.add(doc.docKey)
    candidates.add(getFileName(doc.docKey))
  }
  if (doc.docName) {
    candidates.add(doc.docName)
    candidates.add(getFileName(doc.docName))
  }
  if (doc.url) {
    candidates.add(doc.url)
    candidates.add(doc.url.replace(/^\/+/, ''))
  }
  if (doc.id) {
    candidates.add(String(doc.id))
  }

  // 2. 基于文件夹层级的逻辑全路径
  const { folderNames, folderCodes } = getDocAncestorFolderPaths(doc, folders)
  const name = doc.docName || (!isUuid(doc.docKey) ? doc.docKey : '')

  if (name) {
    if (folderNames.length > 0) {
      candidates.add(`${folderNames.join('/')}/${name}`)
    }
    if (folderCodes.length > 0) {
      candidates.add(`${folderCodes.join('/')}/${name}`)
    }
  }

  if (doc.docKey && !isUuid(doc.docKey)) {
    if (folderNames.length > 0) {
      candidates.add(`${folderNames.join('/')}/${doc.docKey}`)
    }
    if (folderCodes.length > 0) {
      candidates.add(`${folderCodes.join('/')}/${doc.docKey}`)
    }
  }

  return Array.from(candidates).filter(Boolean)
}

/**
 * 在项目文档列表中多策略智能匹配目标文档（同时支持 docKey、文件名、逻辑路径、标题及 API URL）
 * @param {Array} docs 项目文档列表 (projectItem.docs)
 * @param {string} targetPath 目标相对路径 (如 "config/db.md"、"快速开始"、"01-guide/start")
 * @param {Array} [folders] 项目文件夹列表 (projectItem.folders)
 * @returns {Object|null} 匹配到的目标文档对象
 */
export const findMatchingDoc = (docs = [], targetPath = '', folders = []) => {
  if (!targetPath || !docs || !docs.length) return null

  const cleanTarget = targetPath.replace(/\\/g, '/').replace(/^\/+/, '')
  const normTarget = normalizePath(cleanTarget)
  const fuzzyTarget = normalizeFuzzy(cleanTarget)
  const targetFileName = getFileName(cleanTarget)
  const normTargetFileName = normalizePath(targetFileName)

  // 预先计算所有文档的候选特征集
  const docCandidatesList = docs.map(d => ({
    doc: d,
    candidates: getDocCandidateKeys(d, folders)
  }))

  // 策略 1: 精确匹配任意候选路径（包括 docKey、逻辑全路径、docName）
  for (const item of docCandidatesList) {
    if (item.candidates.some(c => c.replace(/\\/g, '/').replace(/^\/+/, '') === cleanTarget)) {
      return item.doc
    }
  }

  // 策略 2: 忽略大小写匹配候选路径
  const lowerCleanTarget = cleanTarget.toLowerCase()
  for (const item of docCandidatesList) {
    if (item.candidates.some(c => c.replace(/\\/g, '/').replace(/^\/+/, '').toLowerCase() === lowerCleanTarget)) {
      return item.doc
    }
  }

  // 策略 3: 去除扩展名规范化匹配 (如 "guide/install" 匹配 "guide/install.md")
  for (const item of docCandidatesList) {
    if (item.candidates.some(c => normalizePath(c) === normTarget)) {
      return item.doc
    }
  }

  // 策略 4: 目录 README/index 匹配（当链接写为目录形式如 "guide" 时）
  for (const item of docCandidatesList) {
    if (item.candidates.some(c => normalizePath(c) === `${normTarget}/readme` || normalizePath(c) === `${normTarget}/index`)) {
      return item.doc
    }
  }

  // 策略 5: 忽略序号前缀的模糊匹配（如 "01-guide/02-start.md" 匹配 "guide/start"）
  for (const item of docCandidatesList) {
    if (item.candidates.some(c => normalizeFuzzy(c) === fuzzyTarget)) {
      return item.doc
    }
  }

  // 策略 6: 单文件名 / 单文档名匹配（跨目录简写时的容错匹配）
  for (const item of docCandidatesList) {
    if (item.candidates.some(c => {
      const fn = getFileName(c)
      return fn.toLowerCase() === targetFileName.toLowerCase() ||
        normalizePath(fn) === normTargetFileName ||
        normalizeFuzzy(fn) === normalizeFuzzy(targetFileName)
    })) {
      return item.doc
    }
  }

  // 策略 7: 文档 docName 包含匹配或部分匹配
  for (const item of docCandidatesList) {
    if (item.doc.docName) {
      const dName = item.doc.docName.trim()
      if (dName.toLowerCase() === targetFileName.toLowerCase() ||
          normalizePath(dName) === normTargetFileName ||
          normalizeFuzzy(dName) === normalizeFuzzy(targetFileName) ||
          dName.toLowerCase() === cleanTarget.toLowerCase()) {
        return item.doc
      }
    }
  }

  return null
}

/**
 * 获取文档的所有父级文件夹 ID / treeId 列表（用于左侧目录树自动展开）
 * @param {Object} doc 目标文档
 * @param {Array} folders 项目文件夹列表
 * @returns {Array<string>} 祖先文件夹 treeId 列表
 */
export const getDocAncestorTreeIds = (doc, folders = []) => {
  const treeIds = []
  if (!doc) return treeIds

  if (doc.parent) {
    let curr = doc.parent
    const visited = new Set()
    while (curr && !visited.has(curr.id || curr.treeId)) {
      visited.add(curr.id || curr.treeId)
      if (curr.treeId) {
        treeIds.push(curr.treeId)
      } else if (curr.id) {
        treeIds.push(`folder_${curr.id}`)
      }
      curr = curr.parent
    }
    return treeIds
  }

  if (doc.folderId && folders && folders.length) {
    const folderMap = Object.fromEntries(folders.map(f => [f.id, f]))
    let currFolder = folderMap[doc.folderId]
    const visited = new Set()
    while (currFolder && !visited.has(currFolder.id)) {
      visited.add(currFolder.id)
      treeIds.push(currFolder.treeId || `folder_${currFolder.id}`)
      currFolder = currFolder.parentId ? folderMap[currFolder.parentId] : currFolder.parent
    }
  }

  return treeIds
}

/**
 * 智能平滑滚动至对应锚点标题
 * @param {string} anchor 锚点名称或 ID
 * @param {Object} options 滚动配置选项 { scrollElement, offsetTop }
 * @returns {boolean} 是否成功找到并滚动
 */
export const scrollToAnchor = (anchor, options = {}) => {
  if (!anchor || typeof anchor !== 'string') return false
  const cleanAnchor = anchor.trim().replace(/^#+/, '')
  if (!cleanAnchor) return false

  let targetEl = null

  // 1. 直接通过 ID 查找
  try {
    targetEl = document.getElementById(cleanAnchor)
    if (!targetEl) {
      targetEl = document.getElementById(encodeURIComponent(cleanAnchor))
    }
    if (!targetEl) {
      targetEl = document.getElementById(cleanAnchor.toLowerCase().replace(/\s+/g, '-'))
    }
  } catch {
    // 忽略选择器异常
  }

  // 2. 在预览容器内查找
  const container = document.querySelector('.md-doc-container') ||
    document.querySelector('.markdown-doc-viewer') ||
    document.querySelector('.md-editor-preview') ||
    document
  if (!targetEl && container) {
    try {
      targetEl = container.querySelector(`[id="${CSS.escape(cleanAnchor)}"]`)
    } catch {
      // 忽略 CSS.escape 异常
    }
  }

  // 3. 降级：在标题元素 (h1~h6) 中按文本内容匹配
  if (!targetEl && container) {
    const headings = container.querySelectorAll('h1, h2, h3, h4, h5, h6')
    const lowerAnchor = cleanAnchor.toLowerCase()
    for (const h of headings) {
      const text = h.textContent.trim().toLowerCase()
      if (text === lowerAnchor || text.includes(lowerAnchor)) {
        targetEl = h
        break
      }
    }
  }

  if (targetEl) {
    const scrollSelector = options.scrollElement || '.markdown-doc-viewer .md-editor-preview-wrapper'
    const scrollEl = typeof scrollSelector === 'string' ? document.querySelector(scrollSelector) : scrollSelector

    if (scrollEl && scrollEl.contains(targetEl)) {
      const targetRect = targetEl.getBoundingClientRect()
      const scrollRect = scrollEl.getBoundingClientRect()
      const topOffset = options.offsetTop || 20
      const targetScrollTop = scrollEl.scrollTop + (targetRect.top - scrollRect.top) - topOffset
      scrollEl.scrollTo({ top: Math.max(0, targetScrollTop), behavior: 'smooth' })
    } else {
      targetEl.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
    return true
  }

  return false
}

/**
 * 格式化文档为 Markdown 引用链接语法
 * @param {Object} doc 文档对象
 * @param {Array} folders 项目文件夹列表
 * @returns {string} 例如 "[快速开始](./guide/start.md)" 或 "[用户详情](/api/v1/users/{id})"
 */
export const formatDocMarkdownLink = (doc, folders = []) => {
  if (!doc) return ''
  const name = doc.docName || doc.url || doc.operationId || (!isUuid(doc.docKey) ? doc.docKey : '') || '文档'

  // API 接口链接
  if (doc.docType === 'api') {
    const url = doc.url ? (doc.url.startsWith('/') ? doc.url : `/${doc.url}`) : ''
    return `[${name}](${url})`
  }

  // Markdown 文档链接
  const effectivePath = getDocEffectivePath(doc, folders)
  const path = (effectivePath.endsWith('.md') || effectivePath.endsWith('.markdown'))
    ? effectivePath
    : `${effectivePath}.md`
  return `[${name}](./${path.replace(/^\/+/, '')})`
}

export default {
  resolveRelativeDocPath,
  findMatchingDoc,
  getDocAncestorTreeIds,
  getDocEffectivePath,
  getDocCandidateKeys,
  formatDocMarkdownLink,
  scrollToAnchor,
  setPendingAnchor,
  consumePendingAnchor,
  normalizePath,
  normalizeFuzzy
}
