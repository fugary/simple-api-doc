import { processTreeData, formatDate } from '@/utils'
import { $i18nBundle } from '@/messages'
import { cloneDeep } from 'lodash-es'

// 递归检查文件夹及其子文件夹是否包含文档
export const containsDocs = (folder) => {
  if (folder.children) {
    // 检查文件夹的子文件夹中是否包含文档
    folder.children = folder.children.filter(child => child.isDoc || containsDocs(child))
    return folder.children.length > 0
  }
  return false
}

// 过滤文件夹，只保留那些包含文档的文件夹，保持原始结构
export const filterFoldersWithDocs = (folders) => {
  return folders.filter(folder => containsDocs(folder))
}

// 已经计算过数量的话，过滤文件夹更加简单
export const filterFoldersWithDocsNew = (folders) => {
  return folders.filter(folder => {
    if (folder.children?.length) {
      folder.children = filterFoldersWithDocsNew(folder.children)
    }
    return folder.childDocCount || folder.isDoc
  })
}

export const calcFolderDocCount = (folders) => {
  let count = 0
  for (const folder of folders) {
    // 初始化子文档计数
    let childCount = 0
    // 递归计算子文件夹的文档数量
    if (folder.children?.length) {
      childCount = calcFolderDocCount(folder.children)
    }
    // 如果当前节点是文档，则计数加1
    if (folder.isDoc) {
      count++
    } else {
      // 当前文件夹的子文档计数应该包括子文件夹的文档数量
      folder.childDocCount = childCount
    }
    // 将子文档数量加到总计数
    count += childCount
  }
  return count
}

export const RECENT_DAYS = 30
export const RECENT_DAYS_MS = RECENT_DAYS * 24 * 60 * 60 * 1000
export const SEVEN_DAYS_MS = RECENT_DAYS_MS

/**
 * 计算文档最近状态类型（30天内创建标 NEW，30天内更新标 UPD）
 * @param doc
 * @param now
 * @returns {'UPD'|'NEW'|null}
 */
export const calcDocRecentType = (doc, now = Date.now()) => {
  if (!doc) return null
  const createTime = doc.createDate ? new Date(doc.createDate).getTime() : 0
  const modifyTime = doc.modifyDate ? new Date(doc.modifyDate).getTime() : 0

  // 30天内更新：modifyDate 存在且在30天内，且版本 > 1 或修改时间晚于创建时间超过1秒
  const isRecentModify = modifyTime && (now - modifyTime <= RECENT_DAYS_MS) &&
    ((doc.version && doc.version > 1) || (createTime && modifyTime - createTime > 1000))
  if (isRecentModify) {
    return 'UPD'
  }

  // 30天内创建：createDate 存在且在30天内
  const isRecentCreate = createTime && (now - createTime <= RECENT_DAYS_MS)
  if (isRecentCreate) {
    return 'NEW'
  }
  return null
}

/**
 * 计算文档最近状态 Tooltip 提示文案
 * @param doc
 * @returns {string}
 */
export const calcDocRecentTooltip = (doc) => {
  if (!doc) return ''
  const type = doc.recentType || calcDocRecentType(doc)
  if (type === 'NEW') {
    const timeStr = doc.createDate ? formatDate(doc.createDate) : ''
    return $i18nBundle('api.msg.recentCreatedTooltip', [timeStr])
  } else if (type === 'UPD') {
    const timeStr = doc.modifyDate ? formatDate(doc.modifyDate) : ''
    return $i18nBundle('api.msg.recentUpdatedTooltip', [timeStr, doc.version || 1])
  }
  return ''
}

/**
 * 获取文档最近状态信息（类型与 Tooltip）
 * @param doc
 * @returns {{type: ('NEW'|'UPD'), tooltip: string}|null}
 */
export const getDocRecentInfo = (doc) => {
  if (!doc) return null
  const type = doc.recentType || calcDocRecentType(doc)
  if (!type) return null
  return {
    type,
    tooltip: calcDocRecentTooltip(doc)
  }
}

/**
 * 计算ProjectItem
 * @param projectItem
 * @param searchParam
 * @param preference {{showDocLabelType: string, lastExpandKeys: string[], lastDocId: number, defaultShowLabel}}
 * @return {{treeNodes: *[], defaultExpandedKeys: *[]}}
 */
export const calcProjectItem = (projectItem, searchParam, preference) => {
  let docTreeNodes = []
  let currentSelectDoc = null
  if (projectItem) {
    projectItem.docs?.sort((a, b) => {
      return a.sortId - b.sortId
    })
    let docs = projectItem.docs || []
    const folders = projectItem.folders || []
    const folderMap = Object.fromEntries(folders.map(folder => [folder.id, folder]))
    docs = docs.filter(doc => !!folderMap[doc.folderId]).map(doc => {
      const parentFolder = folderMap[doc.folderId]
      const children = parentFolder.children = parentFolder.children || []
      children.push(doc)
      doc.label = doc[preference?.defaultShowLabel] || doc.docName || doc.url
      doc.isDoc = true
      doc.parent = parentFolder
      doc.treeId = doc.id
      doc.recentType = calcDocRecentType(doc)
      return doc
    })
    docTreeNodes = processTreeData(projectItem.folders, null, {
      clone: false,
      after: node => {
        node.label = node.label || node.folderName
        node.treeId = 'folder_' + node.id
      }
    })
    calcFolderDocCount(docTreeNodes)
    if (searchParam?.keyword || searchParam?.recentType || projectItem.projectCode !== preference?.preferenceId) {
      docTreeNodes = filterFoldersWithDocsNew(docTreeNodes)
    }
    if ((searchParam?.keyword || searchParam?.recentType) && docTreeNodes.length && preference?.lastExpandKeys) {
      const expandKeySet = new Set(preference.lastExpandKeys)
      getFolderTreeIds({ children: docTreeNodes }).forEach(id => expandKeySet.add(id))
      preference.lastExpandKeys = [...expandKeySet]
    }
    if (docTreeNodes[0]?.id && preference?.lastExpandKeys && !preference.lastExpandKeys?.includes(docTreeNodes[0]?.treeId)) {
      preference.lastExpandKeys.push(docTreeNodes[0]?.treeId)
    }
    currentSelectDoc = docs.find(doc => doc.id === preference?.lastDocId) || docs[0]
    if (preference) {
      preference.lastDocId = currentSelectDoc?.id
      preference.docStatusMap = Object.fromEntries(docs.map(doc => [
        String(doc.id),
        {
          enabled: doc.enabled !== false && doc.status !== 0 && doc.deleted !== true,
          deprecated: Boolean(doc.deprecated)
        }
      ]))
    }
  }
  return {
    docTreeNodes,
    currentSelectDoc,
    projectItem
  }
}
/**
 * 过滤ProjectItem数据
 * @param projectItem
 * @param searchParam 关键字字符串或包含 keyword/recentType 的对象
 * @return {*}
 */
export const filterProjectItem = (projectItem, searchParam) => {
  projectItem = cloneDeep(projectItem)
  if (!projectItem) return projectItem
  const keyword = typeof searchParam === 'string' ? searchParam : searchParam?.keyword
  const recentType = typeof searchParam === 'object' ? searchParam?.recentType : null
  if (keyword) {
    const kw = keyword.toLowerCase()
    projectItem.docs = projectItem.docs?.filter(doc => doc.docName?.toLowerCase().includes(kw) ||
        doc.url?.toLowerCase().includes(kw))
  }
  if (recentType) {
    projectItem.docs = projectItem.docs?.filter(doc => (doc.recentType || calcDocRecentType(doc)) === recentType)
  }
  return projectItem
}

/**
 * 仅保留ProjectItem中的api数据
 * @param projectItem
 * @param keyword
 * @return {*}
 */
export const filterApiProjectItem = (projectItem) => {
  projectItem = cloneDeep(projectItem)
  if (projectItem) {
    projectItem.docs = projectItem.docs?.filter(doc => doc.docType === 'api')
  }
  return projectItem
}

/**
 * 获取folder路径
 * @param node
 * @return {*[]}
 */
export const getFolderPaths = node => {
  const paths = []
  while (node) {
    if (node.folderName) {
      paths.push(node.folderName)
    }
    node = node.parent
  }
  return paths.reverse()
}

export const getFolderIds = (data, ids = []) => {
  if (data.id) {
    ids.push(data.id)
    data.children?.filter(child => !child.isDoc).forEach(child => getFolderIds(child, ids))
  }
  return ids
}

export const getFolderTreeIds = (data, ids = []) => {
  if (data.treeId) {
    ids.push(data.treeId)
    data.children?.filter(child => !child.isDoc).forEach(child => getFolderTreeIds(child, ids))
  }
  return ids
}
