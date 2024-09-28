import { processTreeData } from '@/utils'
import { cloneDeep } from 'lodash-es'

// 递归检查文件夹及其子文件夹是否包含文档
export const containsDocs = (folder) => {
  if (folder.children) {
    // 检查文件夹的子文件夹中是否包含文档
    return folder.children.some(child => child.isDoc || containsDocs(child))
  }
  return false
}

// 过滤文件夹，只保留那些包含文档的文件夹，保持原始结构
export const filterFoldersWithDocs = (folders) => {
  return folders.filter(folder => {
    // 保留包含文档的文件夹
    return containsDocs(folder)
  })
}

/**
 * 计算ProjectItem
 * @param projectItem
 * @param lastSelectDoc
 * @return {{treeNodes: *[], defaultExpandedKeys: *[]}}
 */
export const calcProjectItem = (projectItem, lastSelectDoc) => {
  let docTreeNodes = []
  let docExpandedKeys = []
  let currentSelectDoc = lastSelectDoc
  if (projectItem) {
    projectItem.docs?.sort((a, b) => {
      return a.sortId - b.sortId
    })
    const docs = projectItem.docs || []
    const folders = projectItem.folders || []
    const folderMap = Object.fromEntries(folders.map(folder => [folder.id, folder]))
    docs.forEach(doc => {
      const children = folderMap[doc.folderId].children = folderMap[doc.folderId].children || []
      children.push(doc)
      doc.label = doc.docName
      doc.isDoc = true
    })
    docTreeNodes = processTreeData(projectItem.folders, null, {
      clone: false,
      after: node => (node.label = node.label || node.folderName)
    })
    docTreeNodes = filterFoldersWithDocs(docTreeNodes)
    if (docTreeNodes[0]?.id) {
      docExpandedKeys = [docTreeNodes[0]?.id]
    }
    if (!docs.find(doc => doc.id === currentSelectDoc?.id)) {
      currentSelectDoc = docs[0]
    }
    console.log('=============================treeNodes', docExpandedKeys, docTreeNodes)
  }
  return {
    docTreeNodes,
    docExpandedKeys,
    currentSelectDoc
  }
}
/**
 * 过滤ProjectItem数据
 * @param projectItem
 * @param keyword
 * @return {*}
 */
export const filerProjectItem = (projectItem, keyword) => {
  projectItem = cloneDeep(projectItem)
  if (keyword && projectItem) {
    projectItem.docs = projectItem.docs?.filter(doc => doc.docName?.includes(keyword) || doc.url?.includes(keyword))
  }
  return projectItem
}
