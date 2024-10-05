import { computed, ref } from 'vue'
import ApiFolderApi, { loadAvailableFolders } from '@/api/ApiFolderApi'
import { $coreConfirm, calcAffixOffset, processTreeData } from '@/utils'
import { $i18nBundle } from '@/messages'
import ApiDocApi from '@/api/ApiDocApi'

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
 * @param handlerData
 */
export const getFolderHandlers = (folder, searchParam, handlerData) => {
  return [{
    icon: 'FolderAdd',
    label: '新增子文件夹',
    handler: () => {
      console.log('新增文件夹', folder)
    }
  }, {
    icon: 'DocumentAdd',
    label: '新建接口',
    handler: () => {
      console.log('新建接口')
    }
  }, {
    icon: 'DocumentAdd',
    label: '新建文档',
    handler: () => {
      console.log('新建文档')
    }
  }, calcShowDocLabelHandler(folder, searchParam), {
    enabled: !folder.rootFlag,
    icon: 'Delete',
    type: 'danger',
    label: '删除',
    handler: () => {
      $coreConfirm($i18nBundle('common.msg.commonDeleteConfirm', [folder.folderName]))
        .then(() => ApiFolderApi.deleteById(folder.id))
        .then(() => handlerData.refreshProjectItem())
    }
  }]
}
/**
 * doc处理工具
 * @param doc
 * @param handlerData
 */
export const getDocHandlers = (doc, handlerData) => {
  const isApi = doc.docType === 'api'
  const label = isApi ? '接口' : '文档'
  return [{
    icon: 'Edit',
    label: '编辑' + label,
    handler: () => {
      handlerData.showDocDetails(doc, true)
    }
  }, {
    icon: 'Delete',
    type: 'danger',
    label: '删除' + label,
    handler: () => {
      console.log('删除' + label, doc, handlerData)
      $coreConfirm($i18nBundle('common.msg.commonDeleteConfirm', [doc.docName]))
        .then(() => ApiDocApi.deleteById(doc.id))
        .then(() => handlerData.refreshProjectItem())
    }
  }]
}
/**
 * Node icon计算逻辑
 * @param data
 * @return {string|string}
 */
export const calcNodeLeaf = (data) => {
  if (!data.isDoc) {
    return 'Folder'
  }
  return data.docType === 'md' ? 'custom-markdown' : 'custom-api'
}

export const useFolderDropdown = () => {
  const delayDropdown = ref(false)
  let lastTimer = null
  const leaveDropdown = (data) => { // 离开时延迟执行，方便特殊处理显示问题
    if (delayDropdown.value) {
      lastTimer = setTimeout(() => (data.showOperations = false), 450)
    } else {
      data.showOperations = false
    }
  }
  const enterDropdown = (data) => {
    showDropdown(data, true)
    lastTimer && clearTimeout(lastTimer) // 清理timer
  }
  const showDropdown = (data, delay = true) => {
    data.showOperations = true
    delayDropdown.value = delay
  }
  return {
    delayDropdown,
    enterDropdown,
    leaveDropdown,
    showDropdown
  }
}

export const useFolderTreeNodes = (projectId) => {
  const folderTreeNodes = ref([])
  const folders = ref([])
  const loadValidFolders = projId => {
    return loadAvailableFolders(projId).then(validFolders => {
      folders.value = validFolders || []
      folderTreeNodes.value = processTreeData(validFolders, null, {
        clone: false,
        after: node => (node.label = node.folderName)
      })
    })
  }
  if (projectId) {
    loadValidFolders(projectId)
  }
  return { folderTreeNodes, folders, loadValidFolders }
}

export const useFolderLayoutHeight = (editable, heightFix = 0) => {
  let offset = -70
  if (editable) {
    offset = calcAffixOffset()
  }
  offset = offset - heightFix
  return computed(() => {
    return `calc(100vh - ${220 + offset}px)`
  })
}
