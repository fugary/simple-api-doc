import { computed, ref } from 'vue'
import ApiFolderApi, { loadAvailableFolders } from '@/api/ApiFolderApi'
import { $coreAlert, $coreConfirm, calcAffixOffset, processTreeData } from '@/utils'
import { $i18nBundle, $i18nKey } from '@/messages'
import ApiDocApi from '@/api/ApiDocApi'
import { useWindowSize } from '@vueuse/core'
import { checkDownloadDocs, downloadShareDocs } from '@/api/SimpleShareApi'

/**
 * 根目是否显示url或者名称
 * @param folder
 * @param preference
 * @return {{handler: *, icon: string, labelKey: (string), enabled: boolean}}
 */
export const calcShowDocLabelHandler = (folder, preference) => {
  return {
    enabled: !!folder.rootFlag,
    icon: preference.defaultShowLabel === 'docName' ? 'Link' : 'TextSnippetOutlined',
    labelKey: preference.defaultShowLabel === 'docName' ? 'api.label.docLabelShowUrl' : 'api.label.docLabelShowName',
    handler: () => {
      preference.defaultShowLabel = preference.defaultShowLabel === 'url' ? 'docName' : 'url'
    }
  }
}

export const getDownloadDocsHandlers = (shareDoc) => {
  if (shareDoc.shareId && shareDoc.exportEnabled) {
    const supportedTypes = ['json', 'yaml']
    const shareId = shareDoc.shareId
    return supportedTypes.map(type => {
      return {
        icon: `custom-icon-${type}`,
        label: $i18nKey('common.label.commonDownload', `common.label.${type}`),
        handler: () => {
          checkDownloadDocs(shareId).then(data => {
            console.log('===========================data', data)
            downloadShareDocs({ type, shareId })
          })
        }
      }
    })
  }
  return []
}

/**
 * folder处理工具
 * @param folder
 * @param preference
 * @param handlerData
 */
export const getFolderHandlers = (folder, preference, handlerData) => {
  const statusLabel = folder.status === 1 ? 'common.label.commonDisable' : 'common.label.commonEnable'
  return [{
    icon: 'FolderAdd',
    label: $i18nKey('common.label.commonAdd', 'api.label.subFolder'),
    handler: () => {
      handlerData.addOrEditFolder(null, folder)
    }
  }, {
    enabled: !folder.rootFlag,
    icon: 'Edit',
    label: $i18nKey('common.label.commonEdit', 'api.label.folder'),
    handler: () => {
      console.log('==============================folder', folder)
      handlerData.addOrEditFolder(folder.id, folder.parent)
    }
  }, {
    enabled: !folder.rootFlag,
    icon: folder.status === 1 ? 'CheckBoxOutlined' : 'CheckBoxOutlineBlankFilled',
    type: folder.status === 1 ? 'warning' : '',
    label: $i18nKey(statusLabel, 'api.label.folder'),
    handler: () => {
      console.log('==============================folder', folder)
      $coreConfirm($i18nBundle('common.msg.commonConfirm', [$i18nKey(statusLabel, 'api.label.folder')]))
        .then(() => ApiFolderApi.saveOrUpdate({ ...folder, status: folder.status === 1 ? 0 : 1, parent: null, children: null }))
        .then(() => handlerData.refreshProjectItem())
    }
  }, {
    icon: 'custom-api',
    label: $i18nKey('common.label.commonAdd', 'api.label.interfaces'),
    handler: () => {
      $coreAlert('暂未实现')
    }
  }, {
    icon: 'custom-markdown',
    label: $i18nKey('common.label.commonAdd', 'api.label.mdDocument'),
    handler: () => {
      handlerData.showDocDetails({
        isDoc: true,
        folderId: folder.id,
        projectId: folder.projectId,
        docType: 'md',
        status: 1,
        sortId: getChildrenSortId(folder)
      }, true)
    }
  }, calcShowDocLabelHandler(folder, preference), {
    enabled: !folder.rootFlag,
    icon: 'FolderDelete',
    type: 'danger',
    label: $i18nBundle('common.label.delete'),
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
  const label = isApi ? $i18nBundle('api.label.interfaces') : $i18nBundle('api.label.mdDocument')
  const statusLabel = doc.status === 1 ? 'common.label.commonDisable' : 'common.label.commonEnable'
  return [{
    enabled: !isApi,
    icon: 'Edit',
    label: $i18nBundle('common.label.commonEdit', [label]),
    handler: () => {
      handlerData.showDocDetails(doc, true)
    }
  }, {
    icon: doc.status === 1 ? 'CheckBoxOutlined' : 'CheckBoxOutlineBlankFilled',
    type: doc.status === 1 ? 'warning' : '',
    label: $i18nBundle(statusLabel, [label]),
    handler: () => {
      $coreConfirm($i18nBundle('common.msg.commonConfirm', [$i18nBundle(statusLabel, [label])]))
        .then(() => ApiDocApi.saveOrUpdate({ ...doc, status: doc.status === 1 ? 0 : 1, parent: null }))
        .then(() => handlerData.refreshProjectItem())
    }
  }, {
    icon: 'Delete',
    type: 'danger',
    label: $i18nBundle('common.label.commonDelete', [label]),
    handler: () => {
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

export const useFolderLayoutHeight = (editable, heightFix = 0, force = false) => {
  const { width } = useWindowSize()
  if (width.value <= 768 && !force) {
    return ref('auto')
  }
  let offset = -70
  if (editable) {
    offset = calcAffixOffset()
  }
  offset = offset - heightFix
  return computed(() => {
    return `calc(100vh - ${220 + offset}px)`
  })
}

export const getChildrenSortId = (folder) => {
  const maxSort = 10
  return (folder?.children?.reduce((sortId, child) => Math.max(sortId, child.sortId || 10), maxSort) || maxSort) + 10
}
