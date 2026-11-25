/**
 * Pure utilities for collecting docs from the folder tree and formatting them
 * as line-by-line clipboard text (TSV, CSV, Markdown table, or plain text).
 */

export const COPY_LIST_FORMATS = ['tsv', 'csv', 'markdown', 'text']

export const COPY_LIST_FIELDS = [
  'docName',
  'method',
  'url',
  'folderPath',
  'docType',
  'status'
]

export const DEFAULT_COPY_LIST_CONFIG = {
  format: 'tsv',
  fields: ['docName', 'method', 'url'],
  includeHeader: true,
  onlyApi: false,
  excludeDisabled: false,
  excludeDeprecated: false,
  deduplicate: false,
  textSeparator: ' '
}

const DEFAULT_FIELD_LABELS = {
  docName: 'Name',
  method: 'Method',
  url: 'URL',
  folderPath: 'Folder',
  docType: 'Type',
  status: 'Status'
}

const DEFAULT_VALUE_LABELS = {
  docTypeApi: 'API',
  docTypeMd: 'Markdown',
  statusEnabled: 'Enabled',
  statusDisabled: 'Disabled',
  statusDeprecated: 'Deprecated'
}

/**
 * Check whether a document node is enabled (matches ApiProjectService.calcProjectItem).
 */
export const isDocEnabled = (doc) => {
  return Boolean(doc) && doc.enabled !== false && doc.status !== 0 && doc.deleted !== true
}

/**
 * Check whether a document node is an API document.
 */
export const isApiDoc = (doc) => doc?.docType === 'api'

/**
 * Join an environment baseUrl and an API relative path cleanly.
 */
export const resolveDocUrl = (doc, baseUrl = '') => {
  const rawUrl = (doc?.url || '').trim()
  if (!rawUrl) return ''
  const cleanBase = (baseUrl || '').trim().replace(/\/+$/, '')
  if (!cleanBase || !isApiDoc(doc) || /^https?:\/\//i.test(rawUrl)) {
    return rawUrl
  }
  const normalizedPath = rawUrl.startsWith('/') ? rawUrl : `/${rawUrl}`
  return `${cleanBase}${normalizedPath}`
}

/**
 * Collect docs in pre-order tree traversal, preserving non-root folder path metadata.
 * When selectedDocIds is empty, all matching docs in the tree are collected.
 */
export const collectDocsFromTree = (treeNodes = [], selectedDocIds = [], options = {}) => {
  const selectedSet = new Set(
    (Array.isArray(selectedDocIds) ? selectedDocIds : [...(selectedDocIds || [])])
      .filter(id => typeof id === 'number' && !Number.isNaN(id))
  )
  const hasSelection = selectedSet.size > 0
  const { onlyApi = false, excludeDisabled = false, excludeDeprecated = false } = options
  const items = []

  const walk = (nodes, folderSegments) => {
    if (!Array.isArray(nodes)) return
    for (const node of nodes) {
      if (!node) continue
      if (node.isDoc) {
        if (hasSelection && !selectedSet.has(node.id)) continue
        if (onlyApi && !isApiDoc(node)) continue
        if (excludeDisabled && !isDocEnabled(node)) continue
        if (excludeDeprecated && Boolean(node.deprecated)) continue
        items.push({ doc: node, folderSegments })
      } else if (node.children?.length) {
        const nextSegments = node.folderName && !node.rootFlag
          ? [...folderSegments, node.folderName]
          : folderSegments
        walk(node.children, nextSegments)
      }
    }
  }

  walk(treeNodes, [])
  return items
}

const normalizeSingleLine = (value) => String(value ?? '').replace(/[\r\n\t]+/g, ' ').trim()

const escapeDelimitedCell = (value, quotePattern) => {
  const str = normalizeSingleLine(value)
  return quotePattern.test(str) ? `"${str.replace(/"/g, '""')}"` : str
}

const TABULAR_FORMATTERS = {
  csv: {
    delimiter: ',',
    escapeCell: (value) => escapeDelimitedCell(value, /[",]/)
  },
  tsv: {
    delimiter: '\t',
    escapeCell: (value) => escapeDelimitedCell(value, /"/)
  },
  markdown: {
    delimiter: ' | ',
    prefix: '| ',
    suffix: ' |',
    escapeCell: (value) => normalizeSingleLine(value).replace(/(\\*)\|/g, '$1$1\\|')
  }
}

const resolveFieldValue = (item, field, options) => {
  const { doc, folderSegments } = item
  const valueLabels = { ...DEFAULT_VALUE_LABELS, ...(options.valueLabels || {}) }
  switch (field) {
    case 'docName':
      return doc.docName || ''
    case 'method':
      return isApiDoc(doc) ? (doc.method || '').toUpperCase() : ''
    case 'url':
      return resolveDocUrl(doc, options.baseUrl)
    case 'folderPath':
      return folderSegments.join('/')
    case 'docType':
      return isApiDoc(doc) ? valueLabels.docTypeApi : valueLabels.docTypeMd
    case 'status':
      if (!isDocEnabled(doc)) return valueLabels.statusDisabled
      return doc.deprecated ? valueLabels.statusDeprecated : valueLabels.statusEnabled
    default:
      return ''
  }
}

const formatTabularLine = (cells, format) => {
  const { delimiter, prefix = '', suffix = '', escapeCell } = TABULAR_FORMATTERS[format] || TABULAR_FORMATTERS.tsv
  return `${prefix}${cells.map(escapeCell).join(delimiter)}${suffix}`
}

/**
 * Format collected doc items into line-by-line text ready for preview and clipboard.
 */
export const formatCopyRows = (items = [], config = {}) => {
  const merged = { ...DEFAULT_COPY_LIST_CONFIG, ...config }
  const format = COPY_LIST_FORMATS.includes(merged.format) ? merged.format : 'tsv'
  const fields = (merged.fields || []).filter(field => COPY_LIST_FIELDS.includes(field))
  if (!fields.length || !items.length) {
    return { text: '', lines: [], rowCount: 0 }
  }

  const fieldLabels = { ...DEFAULT_FIELD_LABELS, ...(merged.fieldLabels || {}) }
  const textSeparator = merged.textSeparator ?? ' '
  const dataLines = []
  const seen = new Set()

  for (const item of items) {
    const rawValues = fields.map(field => resolveFieldValue(item, field, merged))
    const line = format === 'text'
      ? rawValues.map(normalizeSingleLine).filter(Boolean).join(textSeparator)
      : formatTabularLine(rawValues, format)
    if (format === 'text' && !line) continue
    if (merged.deduplicate) {
      if (seen.has(line)) continue
      seen.add(line)
    }
    dataLines.push(line)
  }

  if (!dataLines.length) {
    return { text: '', lines: [], rowCount: 0 }
  }

  const lines = []
  if (merged.includeHeader && format !== 'text') {
    lines.push(formatTabularLine(fields.map(field => fieldLabels[field] || field), format))
    if (format === 'markdown') {
      lines.push(`| ${fields.map(() => '---').join(' | ')} |`)
    }
  }
  lines.push(...dataLines)

  return { text: lines.join('\n'), lines, rowCount: dataLines.length }
}
