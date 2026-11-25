import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('../src/services/api/DocCopyListService.js', import.meta.url), 'utf8')
const {
  collectDocsFromTree,
  formatCopyRows,
  buildCopyPreview,
  resolveDocUrl
} = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)

const sampleTree = [
  {
    id: 1,
    treeId: 'folder_1',
    folderName: '根目录',
    rootFlag: true,
    children: [
      {
        id: 10,
        treeId: 10,
        isDoc: true,
        docType: 'md',
        docName: '接口说明',
        status: 1
      },
      {
        id: 2,
        treeId: 'folder_2',
        folderName: '账号认证',
        children: [
          {
            id: 11,
            treeId: 11,
            isDoc: true,
            docType: 'api',
            docName: '用户登录',
            method: 'post',
            url: '/api/login',
            status: 1
          },
          {
            id: 12,
            treeId: 12,
            isDoc: true,
            docType: 'api',
            docName: '旧版登录, 含逗号',
            method: 'POST',
            url: '/api/v1/login',
            status: 1,
            deprecated: 1
          },
          {
            id: 13,
            treeId: 13,
            isDoc: true,
            docType: 'api',
            docName: '停用接口|特殊',
            method: 'GET',
            url: '/api/disabled',
            status: 0
          }
        ]
      }
    ]
  }
]

test('按树先序收集文档，支持空选全量、指定选中及状态/类型过滤', () => {
  const allItems = collectDocsFromTree(sampleTree)
  assert.deepEqual(allItems.map(i => i.doc.id), [10, 11, 12, 13])
  assert.deepEqual(allItems[0].folderSegments, [])
  assert.deepEqual(allItems[1].folderSegments, ['账号认证'])

  const selected = collectDocsFromTree(sampleTree, [11, 13])
  assert.deepEqual(selected.map(i => i.doc.id), [11, 13])

  const activeApiOnly = collectDocsFromTree(sampleTree, [], {
    onlyApi: true,
    excludeDisabled: true,
    excludeDeprecated: true
  })
  assert.deepEqual(activeApiOnly.map(i => i.doc.id), [11])
})

test('TSV、CSV、Markdown 表格与无空格类型纯文本格式化正确', () => {
  const items = collectDocsFromTree(sampleTree)

  const tsv = formatCopyRows(items, {
    format: 'tsv',
    fields: ['docName', 'method', 'url'],
    includeHeader: true
  })
  assert.equal(tsv.rowCount, 4)
  assert.equal(tsv.lines[0], 'Name\tMethod\tURL')
  assert.equal(tsv.lines[1], '接口说明\t\t')
  assert.equal(tsv.lines[2], '用户登录\tPOST\t/api/login')

  const csv = formatCopyRows(items, {
    format: 'csv',
    fields: ['docName', 'url'],
    includeHeader: false
  })
  assert.equal(csv.lines[2], '"旧版登录, 含逗号",/api/v1/login')

  const md = formatCopyRows(items, {
    format: 'markdown',
    fields: ['folderPath', 'docName', 'status'],
    includeHeader: true
  })
  assert.equal(md.lines[0], '| Folder | Name | Status |')
  assert.equal(md.lines[1], '| --- | --- | --- |')
  assert.equal(md.lines[4], '| 账号认证 | 旧版登录, 含逗号 | Deprecated |')
  assert.equal(md.lines[5], '| 账号认证 | 停用接口\\|特殊 | Disabled |')

  const plain = formatCopyRows(items, {
    format: 'text',
    fields: ['url', 'docType'],
    textSeparator: ' '
  })
  assert.equal(plain.lines[0], 'Markdown')
  assert.equal(plain.lines[1], '/api/login API')
  assert.ok(!plain.lines.some(line => line.includes('Markdown 文档')))
})

test('环境 BaseURL 拼接、去重和预览截断', () => {
  assert.equal(resolveDocUrl({ docType: 'api', url: 'v1/users' }, 'https://api.example.com/'), 'https://api.example.com/v1/users')
  assert.equal(resolveDocUrl({ docType: 'api', url: 'https://other.com/a' }, 'https://api.example.com/'), 'https://other.com/a')
  assert.equal(resolveDocUrl({ docType: 'md', url: '/readme' }, 'https://api.example.com/'), '/readme')

  const dupItems = [
    { doc: { id: 1, docType: 'api', url: '/a', method: 'GET' }, folderSegments: [] },
    { doc: { id: 2, docType: 'api', url: '/a', method: 'POST' }, folderSegments: [] }
  ]
  const deduped = formatCopyRows(dupItems, {
    format: 'text',
    fields: ['url'],
    deduplicate: true
  })
  assert.deepEqual(deduped.lines, ['/a'])

  const preview = buildCopyPreview({ lines: ['1', '2', '3'], text: '1\n2\n3', rowCount: 3 }, 2)
  assert.equal(preview.truncated, true)
  assert.equal(preview.previewText, '1\n2')
})
