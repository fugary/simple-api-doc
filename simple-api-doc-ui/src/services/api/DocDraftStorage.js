const PREFIX = 'simple-api-doc:draft:v1:'
const MAX_BYTES = 1024 * 1024
const MAX_AGE = 30 * 24 * 60 * 60 * 1000
const TEXT_FIELDS = ['docName', 'docContent', 'method', 'url', 'status', 'operationId']
const isRecord = value => value && typeof value === 'object' && !Array.isArray(value)

export const draftFields = (doc = {}) => Object.fromEntries([
  ...TEXT_FIELDS.map(field => [field, doc[field] ?? '']),
  ...['locked', 'deprecated'].map(field => [field, !!doc[field]])
])

export const modelDraftFields = (model = {}) => ({
  schemaName: model.schemaName ?? '',
  description: model.description ?? '',
  schemaContent: model.schemaContent ?? '',
  locked: !!model.locked,
  infoId: model.infoId ?? null
})

// 比较时忽略合法 JSON 的格式差异，持久化始终保留用户原文。
export const normalizeSchemaContent = content => {
  try { return JSON.stringify(JSON.parse(content)) } catch { return content }
}

export const modelDraftSignature = fields => JSON.stringify({
  ...fields,
  schemaContent: normalizeSchemaContent(fields?.schemaContent)
})

const fieldsFor = type => type === 'model' ? modelDraftFields : draftFields

export const draftKey = (system, user, project, doc, type = 'doc') => {
  if (!user || !project) return null
  if (type === 'model') return PREFIX + JSON.stringify([system, user, project, 'model', doc.id || ['new', doc.infoId]])
  return PREFIX + JSON.stringify([system, user, project, doc.id || ['new', doc.folderId, doc.docType]])
}

export const readDraft = (storage, key, now = Date.now(), type = 'doc') => {
  if (!key) return null
  const raw = storage.getItem(key)
  if (!raw) return null
  let value
  try { value = JSON.parse(raw) } catch { /* 损坏草稿不影响编辑 */ }
  if (!value || value.format !== 1 || !Number.isFinite(value.savedAt) ||
      now - value.savedAt > MAX_AGE || !isRecord(value.fields) || !isRecord(value.base)) {
    storage.removeItem(key)
    return null
  }
  return { ...value, fields: fieldsFor(type)(value.fields), base: fieldsFor(type)(value.base) }
}

// 只清理本编辑器实际读取或写入的草稿，不删除其他标签页后写入的版本。
export const removeDraft = (storage, key, expected) => {
  if (key && expected && storage.getItem(key) === JSON.stringify(expected)) storage.removeItem(key)
}

export const writeDraft = (storage, key, fields, base, version, type = 'doc') => {
  if (!key) throw new Error('Missing draft identity')
  const value = { format: 1, savedAt: Date.now(), fields: fieldsFor(type)(fields), base: fieldsFor(type)(base), version }
  const json = JSON.stringify(value)
  if (new TextEncoder().encode(json).length > MAX_BYTES) throw new Error('Draft too large')
  storage.setItem(key, json)
  return value
}
