import { ref } from 'vue'

/**
 * 根目是否显示url或者名称
 * @param folder
 * @param searchParam
 * @return {{handler: *, icon: string, labelKey: (string), enabled: boolean}}
 */
export const calcShowDocLabelHandler = (folder, searchParam) => {
  return {
    enabled: !!folder.rootFlag,
    icon: searchParam.showDocLabelType === 'docName' ? 'Link' : 'TextSnippetOutlined',
    labelKey: searchParam.showDocLabelType === 'docName' ? 'api.label.docLabelShowUrl' : 'api.label.docLabelShowName',
    handler: () => {
      searchParam.showDocLabelType = searchParam.showDocLabelType === 'url' ? 'docName' : 'url'
    }
  }
}

/**
 * folder处理工具
 * @param folder
 * @param searchParam
 * @param emit
 */
export const getFolderHandlers = (folder, searchParam, emit) => {
  return [{
    icon: 'FolderAdd',
    label: '新增子文件夹',
    handler: () => {
      console.log('新增文件夹', folder)
      emit('toAddFolder', folder)
    }
  }, {
    icon: 'DocumentAdd',
    label: '新建接口',
    handler: () => {
      console.log('新建接口')
      emit('toAddDoc', { folder, docType: 'api' })
    }
  }, {
    icon: 'DocumentAdd',
    label: '新建文档',
    handler: () => {
      console.log('新建文档')
      emit('toAddDoc', { folder, docType: 'md' })
    }
  }, calcShowDocLabelHandler(folder, searchParam), {
    enabled: !folder.rootFlag,
    icon: 'Delete',
    type: 'danger',
    label: '删除',
    handler: () => {
      console.log('删除')
      emit('deleteFolder', folder)
    }
  }]
}
/**
 * doc处理工具
 * @param doc
 * @param emit
 */
export const getDocHandlers = (doc, emit) => {
  const isApi = doc.docType === 'api'
  const label = isApi ? '接口' : '文档'
  return [{
    icon: 'Edit',
    label: '编辑' + label,
    handler: () => {
      console.log('编辑' + label, doc)
      emit('toEditDoc', doc)
    }
  }, {
    icon: 'Delete',
    type: 'danger',
    label: '删除' + label,
    handler: () => {
      console.log('删除' + label, doc)
      emit('deleteDoc', doc)
    }
  }]
}
/**
 * Node icon计算逻辑
 * @param node
 * @return {string|string}
 */
export const calcNodeLeaf = (node) => {
  if (!node.data.isDoc) {
    return 'Folder'
  }
  return node.data.docType === 'md' ? 'custom-markdown' : 'custom-api'
}

export const useFolderDropdown = () => {
  const delayDropdown = ref(false)
  let lastTimer = null
  const leaveDropdown = (node) => { // 离开时延迟执行，方便特殊处理显示问题
    if (delayDropdown.value) {
      lastTimer = setTimeout(() => (node.data.showOperations = false), 450)
    } else {
      node.data.showOperations = false
    }
  }
  const enterDropdown = (node) => {
    showDropdown(node, true)
    lastTimer && clearTimeout(lastTimer) // 清理timer
  }
  const showDropdown = (node, delay = true) => {
    node.data.showOperations = true
    delayDropdown.value = delay
  }
  return {
    delayDropdown,
    enterDropdown,
    leaveDropdown,
    showDropdown
  }
}
