/**
 * Paste-select utilities: parse one URL per line and match against tree document nodes.
 */

/**
 * Extract pathname from a URL (stripping protocol/host, query and hash),
 * then normalise to lowercase without trailing slash.
 */
const normaliseUrl = (raw) => {
  let path = (raw || '').trim()
  if (!path) return ''
  try {
    if (/^https?:\/\//i.test(path)) {
      path = new URL(path).pathname
    }
  } catch {
    // fall through
  }
  path = path.split(/[?#]/)[0].trim().toLowerCase()
  if (path.length > 1 && path.endsWith('/')) {
    path = path.slice(0, -1)
  }
  return path
}

/**
 * Parse pasted text into one candidate per non-empty line.
 *
 * @param {string} text
 * @returns {Array<{ url: string, raw: string }>}
 */
export const parsePasteLines = (text) => {
  if (!text || typeof text !== 'string') return []
  return text
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line && !/^url$/i.test(line))
    .map(raw => ({ url: normaliseUrl(raw), raw }))
    .filter(item => item.url)
}

/**
 * Collect selectable leaf document nodes from a tree.
 */
const collectLeafDocs = (nodes, nodeKey, isNodeDisabled) => {
  const docs = []
  const walk = (list) => {
    if (!Array.isArray(list)) return
    for (const node of list) {
      if (!node) continue
      if (node.isDoc) {
        if (!(isNodeDisabled && isNodeDisabled(node))) {
          docs.push({
            key: node[nodeKey],
            url: normaliseUrl(node.url || '')
          })
        }
      } else if (node.children?.length) {
        walk(node.children)
      }
    }
  }
  walk(nodes)
  return docs
}

/**
 * Match parsed URL lines against tree nodes.
 * Supports exact path match and prefix-stripped suffix match (for URLs with a base path).
 *
 * @param {Array} treeNodes
 * @param {Array<{ url: string, raw: string }>} lines
 * @param {object} [options]
 * @param {string} [options.nodeKey='value']
 * @param {Function} [options.isNodeDisabled]
 * @returns {{ matchedKeys: any[], unmatchedLines: string[] }}
 */
export const matchTreeNodes = (treeNodes, lines, options = {}) => {
  const { nodeKey = 'value', isNodeDisabled } = options
  const docs = collectLeafDocs(treeNodes, nodeKey, isNodeDisabled)

  const urlIndex = new Map()
  for (const doc of docs) {
    if (!doc.url) continue
    const list = urlIndex.get(doc.url) || []
    list.push(doc.key)
    urlIndex.set(doc.url, list)
  }

  const matchedKeys = new Set()
  const unmatchedLines = []

  for (const line of lines) {
    let keys = urlIndex.get(line.url)

    // Fallback: pasted URL includes a base path prefix (e.g. /prod-api/api/user -> /api/user)
    if (!keys?.length) {
      let bestLen = 0
      let suffixKeys = []
      for (const [docUrl, docKeys] of urlIndex) {
        const boundary = docUrl.startsWith('/') ? docUrl : `/${docUrl}`
        if (line.url.endsWith(boundary) && docUrl.length >= bestLen) {
          if (docUrl.length > bestLen) {
            bestLen = docUrl.length
            suffixKeys = [...docKeys]
          } else {
            suffixKeys.push(...docKeys)
          }
        }
      }
      keys = suffixKeys
    }

    if (keys?.length) {
      keys.forEach(k => matchedKeys.add(k))
    } else {
      unmatchedLines.push(line.raw)
    }
  }

  return {
    matchedKeys: [...matchedKeys],
    unmatchedLines
  }
}
