import { processTreeData } from '@/utils'
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
      doc.label = doc[preference.defaultShowLabel] || doc.docName
      doc.isDoc = true
      doc.parent = parentFolder
      doc.treeId = doc.id
      return doc
    })
    docTreeNodes = processTreeData(projectItem.folders, null, {
      clone: false,
      after: node => {
        node.label = node.label || node.folderName
        node.treeId = 'folder_' + node.id
      }
    })
    if (searchParam?.keyword) {
      docTreeNodes = filterFoldersWithDocs(docTreeNodes)
    }
    if (docTreeNodes[0]?.id && !preference.lastExpandKeys.includes(docTreeNodes[0]?.treeId)) {
      preference.lastExpandKeys.push(docTreeNodes[0]?.treeId)
    }
    currentSelectDoc = docs.find(doc => doc.id === preference.lastDocId) || docs[0]
    preference.lastDocId = currentSelectDoc?.id
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
 * @param keyword
 * @return {*}
 */
export const filterProjectItem = (projectItem, keyword) => {
  projectItem = cloneDeep(projectItem)
  if (keyword && projectItem) {
    projectItem.docs = projectItem.docs?.filter(doc => doc.docName?.includes(keyword) || doc.url?.includes(keyword))
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
