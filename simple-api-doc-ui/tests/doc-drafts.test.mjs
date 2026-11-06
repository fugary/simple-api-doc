import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const vue = require('vue')

const source = await readFile(new URL('../src/services/api/DocDraftStorage.js', import.meta.url), 'utf8')
const { draftKey, draftFields, modelDraftFields, modelDraftSignature, normalizeSchemaContent, readDraft, writeDraft, removeDraft } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
const memoryStorage = () => {
  const values = new Map()
  return { getItem: key => values.get(key), setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }
}

test('草稿按用户、系统、项目、文档及新建目录隔离', () => {
  const keys = [
    draftKey('s', 1, 'p', { id: 1 }), draftKey('s', 2, 'p', { id: 1 }),
    draftKey('s2', 1, 'p', { id: 1 }), draftKey('s', 1, 'p2', { id: 1 }),
    draftKey('s', 1, 'p', { id: 2 }), draftKey('s', 1, 'p', { folderId: 1, docType: 'md' }),
    draftKey('s', 1, 'p', { folderId: 2, docType: 'md' }), draftKey('s', 1, 'p', { folderId: 1, docType: 'api' })
  ]
  assert.equal(new Set(keys).size, keys.length)
  assert.equal(draftKey('s', null, 'p', { id: 1 }), null)
})

test('恢复保留可编辑字段，排除版本、认证和项目树', () => {
  const storage = memoryStorage()
  const value = { docName: '标题', docContent: '正文', locked: true, version: 12, token: 'secret', project: { large: true } }
  writeDraft(storage, 'doc', value, { docName: '旧标题' }, 11)
  const draft = readDraft(storage, 'doc')
  assert.equal(draft.fields.docContent, '正文')
  assert.equal(draft.base.docName, '旧标题')
  assert.equal(draft.version, 11)
  assert.equal(draft.fields.version, undefined)
  assert.equal(draft.fields.token, undefined)
  assert.equal(draft.fields.project, undefined)
  assert.deepEqual(draftFields({ docContent: null }), draftFields({ docContent: '' }))
})

test('过期与损坏草稿被清理', () => {
  const storage = memoryStorage()
  const value = writeDraft(storage, 'doc', {}, {}, 1)
  assert.equal(readDraft(storage, 'doc', value.savedAt + 31 * 86400000), null)
  storage.setItem('doc', '{broken')
  assert.equal(readDraft(storage, 'doc'), null)
  assert.equal(storage.getItem('doc'), undefined)
})

test('存储异常和大内容不冒充保存成功且保留原草稿', () => {
  const storage = memoryStorage()
  writeDraft(storage, 'doc', { docContent: '保留' }, {}, 1)
  assert.throws(() => writeDraft(storage, 'doc', { docContent: '中'.repeat(400000) }, {}, 1))
  assert.equal(readDraft(storage, 'doc').fields.docContent, '保留')
  assert.throws(() => writeDraft({ setItem () { throw new Error('quota') } }, 'doc', {}, {}, 1), /quota/)
  assert.throws(() => readDraft({ getItem () { throw new Error('disabled') } }, 'doc'), /disabled/)
})

const hookSource = (await Promise.all(['../src/stores/EditDraftStore.js', '../src/hooks/useEditDraft.js', '../src/hooks/useDocDraft.js'].map(path => readFile(new URL(path, import.meta.url), 'utf8')))).join('\n')
const { defineStore, createPinia, setActivePinia } = require('pinia')
let hookId = 0
const setupEditor = async (t, { storage = memoryStorage(), getById, model = false, confirm = () => Promise.resolve() } = {}) => {
  setActivePinia(createPinia())
  const events = new Map()
  const unmount = []
  const routes = []
  const guard = vue.ref(null)
  const login = vue.reactive({ systemKey: 's', accountInfo: { id: 1 } })
  globalThis.window = { localStorage: storage, addEventListener: (name, fn) => events.set(name, fn), removeEventListener: name => events.delete(name) }
  globalThis.document = { hidden: false, addEventListener () {}, removeEventListener () {} }
  globalThis.draftTestDeps = {
    ...vue,
    defineStore,
    inject: () => guard,
    onActivated () {},
    onBeforeUnmount: fn => unmount.push(fn),
    onDeactivated () {},
    useRouter: () => ({ beforeEach: fn => { routes.push(fn); return () => routes.splice(routes.indexOf(fn), 1) } }),
    useRoute: () => ({ params: { projectCode: 'p' } }),
    ApiDocApi: { getById: getById || (async id => ({ success: true, resultData: { id, docName: '原名称', docContent: '原内容', version: 2 } })) },
    useLoginConfigStore: () => login,
    draftFields,
    modelDraftFields,
    draftKey,
    readDraft,
    writeDraft,
    removeDraft
  }
  const names = Object.keys(globalThis.draftTestDeps).join(',')
  const script = `const {${names}} = globalThis.draftTestDeps;\n` + hookSource.replace(/^import .*\n/gm, '') + `\n// ${hookId++}`
  const { useDocDraft, useEditDraft } = await import(`data:text/javascript;base64,${Buffer.from(script).toString('base64')}`)
  const current = vue.ref(model ? { id: 1, bodyType: 'component', projectId: 7, infoId: 8, schemaContent: '{}', version: 2 } : { id: 1, docType: 'md' })
  const emitted = []
  const scope = vue.effectScope()
  const editor = scope.run(() => model
    ? useEditDraft(current, {
      type: 'model',
      fields: modelDraftFields,
      signature: modelDraftSignature,
      project: value => value.projectId,
      load: async value => ({ ...value }),
      saved: value => emitted.push(value)
    })
    : useDocDraft(current, (...args) => emitted.push(args)))
  const settle = async () => { await Promise.resolve(); await vue.nextTick() }
  scope.run(() => vue.watch(() => editor.draft.leaving, async open => {
    if (!open || !confirm) return
    try {
      await confirm(editor.draft.error ? 'api.msg.draftFailed' : 'api.msg.draftLeave')
      editor.draft.resolveLeave('keep')
    } catch { editor.draft.resolveLeave('stay') }
  }))
  await settle()
  t.after(() => { unmount.forEach(fn => fn()); scope.stop(); delete globalThis.draftTestDeps; delete globalThis.window; delete globalThis.document })
  return { ...editor, current, storage, events, routes, emitted, settle, guard, login }
}

test('未修改页面离开不会清理其他标签页新写入的草稿', async t => {
  const e = await setupEditor(t)
  const key = draftKey('s', 1, 'p', { id: 1 })
  writeDraft(e.storage, key, { docContent: '另一标签页' }, {}, 2)
  e.current.value = { id: 2, docType: 'md' }
  await e.settle()
  assert.equal(readDraft(e.storage, key).fields.docContent, '另一标签页')
})

test('保存完成只清理当前草稿，不删除其他标签页后写入的版本', async t => {
  const e = await setupEditor(t)
  e.model.value.docContent = '当前修改'
  await e.draft.confirmLeave()
  const key = draftKey('s', 1, 'p', { id: 1 })
  writeDraft(e.storage, key, { docContent: '其他修改' }, {}, 2)
  e.onSaved({ id: 1 })
  assert.equal(readDraft(e.storage, key).fields.docContent, '其他修改')
})

test('加载期间切换账号不会显示原账号文档或读取新账号草稿', async t => {
  let completeLoad
  const e = await setupEditor(t, { getById: () => new Promise(resolve => { completeLoad = resolve }) })
  e.login.accountInfo = { id: 2 }
  completeLoad({ success: true, resultData: { id: 1, docContent: '原账号内容' } })
  await e.settle()
  assert.equal(e.model.value, undefined)
  assert.equal(e.draft.loadError, true)
})

test('草稿字段结构损坏时不会恢复为空内容', () => {
  const storage = memoryStorage()
  storage.setItem('doc', JSON.stringify({ format: 1, savedAt: Date.now(), fields: 'broken', base: {} }))
  assert.equal(readDraft(storage, 'doc'), null)
  assert.equal(storage.getItem('doc'), undefined)
})

test('放弃草稿并离开立即清理，后续切换不会重新生成草稿', async t => {
  const e = await setupEditor(t, { confirm: null })
  e.model.value.docContent = '放弃的修改'
  const decision = e.draft.confirmLeave()
  const key = draftKey('s', 1, 'p', { id: 1 })
  assert.ok(readDraft(e.storage, key))
  e.draft.resolveLeave('discard')
  assert.equal(await decision, true)
  assert.equal(e.draft.dirty, false)
  e.current.value = { id: 2, docType: 'md' }
  await e.settle()
  assert.equal(readDraft(e.storage, key), null)
})

test('离开窗口可继续编辑或保留草稿离开', async t => {
  const e = await setupEditor(t, { confirm: null })
  e.model.value.docContent = '保留的修改'
  const stay = e.draft.confirmLeave()
  e.draft.resolveLeave('stay')
  assert.equal(await stay, false)
  assert.equal(e.model.value.docContent, '保留的修改')
  const leave = e.draft.confirmLeave()
  e.draft.resolveLeave('keep')
  assert.equal(await leave, true)
  assert.equal(readDraft(e.storage, draftKey('s', 1, 'p', { id: 1 })).fields.docContent, '保留的修改')
})

test('草稿清理失败保持编辑，用户可取消离开', async t => {
  const e = await setupEditor(t, { confirm: null })
  e.model.value.docContent = '不能丢失'
  const leave = e.draft.confirmLeave()
  e.storage.removeItem = () => { throw new Error('disabled') }
  e.draft.resolveLeave('discard')
  assert.equal(e.draft.leaving, true)
  assert.equal(e.draft.error, true)
  assert.equal(e.model.value.docContent, '不能丢失')
  e.draft.resolveLeave('stay')
  assert.equal(await leave, false)
})

test('保留草稿失败不离开，未落盘的修改仍可明确放弃', async t => {
  const e = await setupEditor(t, { confirm: null })
  e.storage.setItem = () => { throw new Error('quota') }
  e.model.value.docContent = '无法落盘'
  const leave = e.draft.confirmLeave()
  e.draft.resolveLeave('keep')
  assert.equal(e.draft.leaving, true)
  assert.equal(e.draft.error, true)
  assert.equal(e.model.value.docContent, '无法落盘')
  e.draft.resolveLeave('discard')
  assert.equal(await leave, true)
  assert.equal(e.draft.dirty, false)
})

test('取消离开保留修改，刷新立即落盘，成功保存清理草稿', async t => {
  const e = await setupEditor(t, { confirm: () => Promise.reject(new Error('cancel')) })
  e.model.value.docContent = '未保存'
  await e.settle()
  assert.equal(await e.guard.value(), false)
  assert.equal(e.model.value.docContent, '未保存')
  const key = draftKey('s', 1, 'p', { id: 1 })
  assert.equal(readDraft(e.storage, key).fields.docContent, '未保存')
  let prevented = false
  e.events.get('beforeunload')({ preventDefault () { prevented = true } })
  assert.equal(prevented, true)
  assert.equal(e.draft.dirty, true)
  e.onSaved({ id: 1 })
  assert.equal(readDraft(e.storage, key), null)
  assert.equal(e.draft.dirty, false)
  assert.equal(e.emitted.length, 1)
})

test('恢复草稿提示版本变化且不恢复旧服务端版本', async t => {
  const storage = memoryStorage()
  writeDraft(storage, draftKey('s', 1, 'p', { id: 1 }), { docName: '草稿名', docContent: '草稿' }, { docName: '旧名称' }, 1)
  const e = await setupEditor(t, { storage })
  assert.equal(e.model.value.docContent, '原内容')
  assert.equal(e.draft.conflict, true)
  e.draft.restore()
  assert.equal(e.model.value.docContent, '草稿')
  assert.equal(e.model.value.version, 2)
  assert.equal(e.draft.dirty, true)
  assert.equal(await e.routes[0](), true)
})

test('迟到的文档响应不能覆盖后选择的文档', async t => {
  const requests = new Map()
  const e = await setupEditor(t, { getById: id => new Promise(resolve => requests.set(id, resolve)) })
  e.current.value = { id: 2, docType: 'md' }
  await e.settle()
  requests.get(2)({ success: true, resultData: { id: 2, docContent: '第二篇' } })
  await e.settle()
  requests.get(1)({ success: true, resultData: { id: 1, docContent: '第一篇' } })
  await e.settle()
  assert.equal(e.model.value.id, 2)
  assert.equal(e.model.value.docContent, '第二篇')
})

test('存储失败仍保护离开，正式保存期间不能切换', async t => {
  const storage = memoryStorage()
  storage.setItem = () => { throw new Error('quota') }
  let message
  const e = await setupEditor(t, { storage, confirm: text => { message = text; return Promise.reject(new Error('cancel')) } })
  e.model.value.docContent = '重要修改'
  await e.settle()
  assert.equal(await e.draft.confirmLeave(), false)
  assert.equal(message, 'api.msg.draftFailed')
  assert.equal(e.draft.error, true)
  e.saving.value = true
  assert.equal(await e.draft.confirmLeave(), false)
})

test('没有修改不提示离开，丢弃草稿保留最新文档', async t => {
  const storage = memoryStorage()
  const key = draftKey('s', 1, 'p', { id: 1 })
  writeDraft(storage, key, { docContent: '草稿' }, {}, 1)
  const e = await setupEditor(t, { storage, confirm: () => { throw new Error('Unexpected prompt') } })
  e.draft.discard()
  assert.equal(e.model.value.docContent, '原内容')
  assert.equal(e.model.value.version, 2)
  assert.equal(e.draft.pending, undefined)
  assert.equal(readDraft(storage, key), null)
  assert.equal(await e.draft.confirmLeave(), true)
})

test('新建文档自动保存并可再次恢复', async t => {
  const e = await setupEditor(t)
  e.current.value = { docType: 'md', folderId: 8, docName: '新文档' }
  await e.settle()
  e.model.value.docContent = '新正文'
  await new Promise(resolve => setTimeout(resolve, 550))
  const key = draftKey('s', 1, 'p', e.current.value)
  assert.equal(readDraft(e.storage, key).fields.docContent, '新正文')
  e.current.value = { ...e.current.value }
  await e.settle()
  assert.equal(e.draft.pending.fields.docContent, '新正文')
  e.draft.restore()
  assert.equal(e.model.value.docContent, '新正文')
})

test('恢复弹窗关闭保留草稿，恢复和丢弃后不误触发退出编辑', async t => {
  const { parse, compileScript } = require('@vue/compiler-sfc')
  const source = await readFile(new URL('../src/views/components/api/doc/comp/DocDraftWindow.vue', import.meta.url), 'utf8')
  const { descriptor } = parse(source)
  globalThis.recoveryTestComputed = vue.computed
  t.after(() => { delete globalThis.recoveryTestComputed })
  const script = 'const computed = globalThis.recoveryTestComputed;\n' + compileScript(descriptor, { id: 'recovery-test' }).content.replace(/^import .*\n/gm, '')
  const { default: component } = await import(`data:text/javascript;base64,${Buffer.from(script).toString('base64')}`)
  const e = await setupEditor(t)
  e.model.value.docContent = '本地草稿'
  await e.draft.confirmLeave()
  e.current.value = { ...e.current.value }
  await e.settle()
  const events = []
  const windowState = component.setup({ draft: e.draft }, { expose () {}, emit: event => events.push(event) })
  assert.equal(windowState.visible.value, true)
  windowState.visible.value = false
  assert.deepEqual(events, ['cancel'])
  assert.equal(e.draft.pending.fields.docContent, '本地草稿')
  windowState.restore()
  assert.equal(windowState.visible.value, false)
  assert.equal(e.model.value.docContent, '本地草稿')
  windowState.visible.value = false
  assert.equal(events.length, 1)
  e.current.value = { ...e.current.value }
  await e.settle()
  windowState.buttons.value[0].click()
  assert.equal(windowState.visible.value, false)
  assert.equal(e.model.value.docContent, '原内容')
  assert.equal(events.length, 1)
})

test('DTO 与文档同 ID 隔离，新建模型按导入目录隔离', () => {
  const keys = [draftKey('s', 1, 7, { id: 1 }), draftKey('s', 1, 7, { id: 1 }, 'model'),
    draftKey('s', 1, 7, { infoId: 8 }, 'model'), draftKey('s', 1, 7, { infoId: 9 }, 'model')]
  assert.equal(new Set(keys).size, keys.length)
})

test('DTO 未完成的 JSON 原文可恢复，服务端版本不被草稿覆盖', async t => {
  const e = await setupEditor(t, { model: true })
  e.model.value.schemaName = 'OrderDTO'
  e.model.value.schemaContent = '{ "properties": { '
  await e.draft.confirmLeave()
  const key = draftKey('s', 1, 7, { id: 1 }, 'model')
  assert.equal(readDraft(e.storage, key, Date.now(), 'model').fields.schemaContent, '{ "properties": { ')
  e.current.value = { ...e.current.value, version: 3 }
  await e.settle()
  assert.equal(e.draft.conflict, true)
  e.draft.restore()
  assert.equal(e.model.value.schemaContent, '{ "properties": { ')
  assert.equal(e.model.value.version, 3)
  e.onSaved({ id: 1 })
  assert.equal(readDraft(e.storage, key, Date.now(), 'model'), null)
})

test('新建 DTO 草稿保留字段并在放弃后不重新生成', async t => {
  const e = await setupEditor(t, { model: true, confirm: null })
  e.current.value = { bodyType: 'component', projectId: 7, infoId: 8, schemaContent: '{}' }
  await e.settle()
  Object.assign(e.model.value, { schemaName: 'NewDTO', description: '说明', schemaContent: '{', locked: true })
  const leaving = e.draft.confirmLeave()
  const key = draftKey('s', 1, 7, e.current.value, 'model')
  assert.equal(readDraft(e.storage, key, Date.now(), 'model').fields.description, '说明')
  e.draft.resolveLeave('discard')
  assert.equal(await leaving, true)
  assert.equal(e.draft.dirty, false)
  assert.equal(readDraft(e.storage, key, Date.now(), 'model'), null)
})

test('模型源码与树同步保留原始格式和无效 JSON，切换模型不串内容', async t => {
  const source = await readFile(new URL('../src/views/components/api/project/ApiProjectComponent.vue', import.meta.url), 'utf8')
  const block = source.slice(source.indexOf('const schemaContentObj = ref'), source.indexOf('const codeHeight'))
  const current = vue.ref({ schemaContent: '{ "type": "object", "properties": {} }' })
  const content = vue.ref('')
  const scope = vue.effectScope()
  t.after(() => scope.stop())
  const script = 'export const setup = (ref, watch, currentComponentModel, contentRef, normalizeSchemaContent) => {\n' + block + '\nreturn { schemaContentObj, activeTab } }'
  const { setup } = await import(`data:text/javascript;base64,${Buffer.from(script).toString('base64')}`)
  const editor = scope.run(() => setup(vue.ref, vue.watch, current, content, normalizeSchemaContent))
  assert.equal(content.value, '{ "type": "object", "properties": {} }')
  content.value = '{ "type": '
  assert.equal(current.value.schemaContent, '{ "type": ')
  assert.equal(editor.activeTab.value, 'schema')
  content.value = '{ "type": "object", "properties": {"name": {"type": "string"}} }'
  assert.equal(editor.schemaContentObj.value.properties.name.type, 'string')
  assert.equal(current.value.schemaContent, content.value)
  editor.schemaContentObj.value.properties.name.type = 'number'
  assert.equal(JSON.parse(content.value).properties.name.type, 'number')
  current.value = undefined
  current.value = { schemaContent: '{ "type": "boolean" }' }
  assert.equal(content.value, '{ "type": "boolean" }')
  assert.equal(editor.schemaContentObj.value.type, 'boolean')
})

test('DTO 连续保存使用返回的新版本，失败时保持草稿与弹窗', async () => {
  const source = await readFile(new URL('../src/views/components/api/project/ApiProjectComponent.vue', import.meta.url), 'utf8')
  const block = source.slice(source.indexOf('const saveComponent = async'), source.indexOf('const deleteComponent ='))
  const script = 'export const setup = ({ saving, readonly, draft, currentComponentModel, contentRef, checkAndSaveDocInfoDetail, onSaved }) => {\n' + block + '\nreturn saveComponent }'
  const { setup } = await import(`data:text/javascript;base64,${Buffer.from(script).toString('base64')}`)
  const current = vue.ref({ id: 1, version: 2, schemaContent: '{}' })
  const saving = vue.ref(false)
  const versions = []
  let saved = 0
  let fail = false
  const save = setup({
    saving,
    readonly: vue.ref(false),
    draft: {},
    currentComponentModel: current,
    contentRef: vue.ref('{}'),
    checkAndSaveDocInfoDetail: async data => {
      versions.push(data.version)
      if (fail) throw new Error('network')
      return { ...data, version: data.version + 1 }
    },
    onSaved: () => { saved++ }
  })
  const form = { validate: async () => true }
  assert.equal(await save(form), true)
  assert.equal(await save(form), true)
  assert.deepEqual(versions, [2, 3])
  assert.equal(current.value.version, 4)
  fail = true
  assert.equal(await save(form), false)
  assert.equal(saved, 2)
  assert.equal(saving.value, false)
  assert.equal(current.value.version, 4)
})

test('DTO 自动格式化不生成草稿或冲突，真实修改和无效 JSON 仍受保护', async t => {
  const e = await setupEditor(t, { model: true })
  e.current.value = { ...e.current.value, schemaContent: '{"type":"object"}' }
  await e.settle()
  e.model.value.schemaContent = '{\n  "type": "object"\n}'
  assert.equal(e.draft.dirty, false)
  assert.equal(await e.draft.confirmLeave(), true)
  assert.equal(e.storage.getItem(draftKey('s', 1, 7, { id: 1 }, 'model')), undefined)
  e.model.value.description = '实际修改'
  assert.equal(e.draft.dirty, true)
  await e.draft.confirmLeave()
  e.current.value = { ...e.current.value, schemaContent: '{ "type" : "object" }' }
  await e.settle()
  assert.equal(e.draft.conflict, false)
  e.draft.restore()
  assert.equal(e.model.value.schemaContent, '{\n  "type": "object"\n}')
  assert.equal(e.model.value.description, '实际修改')
  e.model.value.schemaContent = '{'
  assert.equal(e.draft.dirty, true)
})
