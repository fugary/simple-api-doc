import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const vue = require('vue')

const source = await readFile(new URL('../src/services/api/DocDraftStorage.js', import.meta.url), 'utf8')
const { draftKey, draftFields, readDraft, writeDraft, removeDraft } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
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

const hookSource = await readFile(new URL('../src/hooks/useDocDraft.js', import.meta.url), 'utf8')
let hookId = 0
const setupEditor = async (t, { storage = memoryStorage(), getById, confirm = () => Promise.resolve() } = {}) => {
  const events = new Map()
  const unmount = []
  const routes = []
  const guard = vue.ref(null)
  const login = vue.reactive({ systemKey: 's', accountInfo: { id: 1 } })
  globalThis.window = { localStorage: storage, addEventListener: (name, fn) => events.set(name, fn), removeEventListener: name => events.delete(name) }
  globalThis.document = { hidden: false, addEventListener () {}, removeEventListener () {} }
  globalThis.draftTestDeps = {
    ...vue,
    inject: () => guard,
    onActivated () {},
    onBeforeUnmount: fn => unmount.push(fn),
    onDeactivated () {},
    onBeforeRouteLeave: fn => routes.push(fn),
    onBeforeRouteUpdate: fn => routes.push(fn),
    useRoute: () => ({ params: { projectCode: 'p' } }),
    ApiDocApi: { getById: getById || (async id => ({ success: true, resultData: { id, docName: '原名称', docContent: '原内容', version: 2 } })) },
    useLoginConfigStore: () => login,
    draftFields,
    draftKey,
    readDraft,
    writeDraft,
    removeDraft
  }
  const names = Object.keys(globalThis.draftTestDeps).join(',')
  const script = `const {${names}} = globalThis.draftTestDeps;\n` + hookSource.replace(/^import .*\n/gm, '') + `\n// ${hookId++}`
  const { useDocDraft } = await import(`data:text/javascript;base64,${Buffer.from(script).toString('base64')}`)
  const current = vue.ref({ id: 1, docType: 'md' })
  const emitted = []
  const scope = vue.effectScope()
  const editor = scope.run(() => useDocDraft(current, (...args) => emitted.push(args)))
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
