import { computed, inject, provide, ref } from 'vue'
import ApiFolderApi, { clearFolder, loadAvailableFolders } from '@/api/ApiFolderApi'
import { $coreAlert, $coreConfirm, processTreeData } from '@/utils'
import { $i18nBundle, $i18nKey } from '@/messages'
import ApiDocApi, { copyApiDoc, updateApiDoc } from '@/api/ApiDocApi'
import {
  checkExportDownloadDocs,
  downloadExportShareDocs
} from '@/api/SimpleShareApi'
import { isFunction } from 'lodash-es'
import { CURRENT_SHARE_THEME_KEY, DEFAULT_PREFERENCE_ID_KEY } from '@/consts/ApiConstants'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import { checkExportProjectDocs, downloadExportProjectDocs } from '@/api/ApiProjectApi'
import { useDark } from '@vueuse/core'
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'

/**
 * 判断是否有API文档
 * @param projectItem
 * @returns {*}
 */
export const checkHasApiDoc = (projectItem) => projectItem?.docs?.some(doc => doc.docType === 'api')

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

export const calcShowMergeAllOfHandler = (folder, preference) => {
  return {
    enabled: !!folder.rootFlag,
    icon: preference.showMergeAllOf ? 'CallSplitFilled' : 'MergeFilled',
    labelKey: preference.showMergeAllOf ? 'api.label.unMergeAllOf' : 'api.label.mergeAllOf',
    handler: () => {
      preference.showMergeAllOf = !preference.showMergeAllOf
    }
  }
}

export const calcDebugInWindowHandler = (folder, preference) => {
  return {
    enabled: !!folder.rootFlag,
    icon: preference.debugInWindow ? 'VerticalSplitFilled' : 'DesktopWindowsFilled',
    labelKey: preference.debugInWindow ? 'api.label.debugInFitScreen' : 'api.label.debugInModalWindow',
    handler: () => {
      preference.debugInWindow = !preference.debugInWindow
    }
  }
}

export const calcShowCleanHandlers = (folder, preference, config = {}) => {
  return preference.preferenceId && config.hasApiDoc?.value
    ? [{
        enabled: !!folder.rootFlag,
        icon: 'DeleteFilled',
        labelKey: 'api.label.clearCachedData',
        type: 'danger',
        handler: () => {
          const { reload } = config
          if (preference?.preferenceId && isFunction(reload)) {
            $coreConfirm($i18nKey('common.msg.commonConfirm', 'api.label.clearCachedData')).then(() => {
              useShareConfigStore().clearSharePreference(preference.preferenceId)
              reload()
            })
          }
        }
      }]
    : []
}

export const getDownloadDocsHandlers = (projectItem, shareDoc, config = {}) => {
  const isShareDoc = shareDoc && !!shareDoc.shareId
  if ((!isShareDoc || shareDoc?.exportEnabled)) {
    const supportedTypes = ['json', 'yaml']
    if (!isShareDoc) {
      supportedTypes.push('md')
    }
    const results = supportedTypes.map(type => {
      return {
        icon: `custom-icon-${type}`,
        label: $i18nKey('common.label.commonExport', `common.label.${type}`),
        enabled: config.hasApiDoc?.value || type === 'md',
        handler: () => {
          const { toShowTreeConfigWindow } = config
          if (isFunction(toShowTreeConfigWindow)) {
            toShowTreeConfigWindow(type, () => {
              $coreConfirm($i18nBundle('api.msg.exportConfirm'))
                .then(() => {
                  const param = { shareId: shareDoc?.shareId, projectCode: projectItem.projectCode, type }
                  const checkDownloadFunc = isShareDoc ? checkExportDownloadDocs : checkExportProjectDocs
                  const downloadExportFunc = isShareDoc ? downloadExportShareDocs : downloadExportProjectDocs
                  checkDownloadFunc(param).then(resData => {
                    if (resData.success && resData.resultData) {
                      downloadExportFunc({
                        ...param, uuid: resData.resultData
                      })
                    }
                  })
                })
            })
          }
        }
      }
    })
    results.push({
      icon: 'custom-icon-zip',
      label: $i18nBundle('api.label.generateClientCode'),
      enabled: config.hasApiDoc?.value,
      handler: () => {
        const { toShowCodeGenConfigWindow } = config
        if (isFunction(toShowCodeGenConfigWindow)) {
          toShowCodeGenConfigWindow()
        }
      }
    })
    return results.filter(result => result.enabled)
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
    enabled: false,
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
        sortId: getMdChildrenSortId(folder)
      }, true)
    }
  }, {
    icon: 'custom-api',
    label: $i18nKey('common.label.commonAdd', 'api.label.interfaces'),
    handler: () => {
      handlerData.showDocDetails({
        isDoc: true,
        folderId: folder.id,
        projectId: folder.projectId,
        docType: 'api',
        status: 1,
        sortId: getMdChildrenSortId(folder)
      }, true)
    }
  }, {
    enabled: !folder.rootFlag && handlerData.isDeletable?.value,
    icon: 'FolderDelete',
    type: 'danger',
    label: $i18nKey('common.label.commonDelete', 'api.label.folder'),
    handler: () => {
      $coreConfirm($i18nBundle('common.msg.commonDeleteConfirm', [folder.folderName]))
        .then(() => ApiFolderApi.deleteById(folder.id))
        .then(() => handlerData.refreshProjectItem())
    }
  }, {
    enabled: handlerData.isDeletable?.value,
    icon: 'Folder',
    type: 'danger',
    label: $i18nKey('common.label.commonClear', 'api.label.folder'),
    handler: () => {
      $coreConfirm($i18nBundle('common.msg.commonClearConfirm', [folder.folderName]))
        .then(() => clearFolder(folder.id))
        .then(() => handlerData.refreshProjectItem())
    }
  }]
}

export const docHandlerSaveDoc = (doc, newData) => {
  return updateApiDoc({ ...doc, ...newData, parent: null })
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
  const lockedLabel = $i18nBundle(doc.locked ? 'api.label.apiDocUnlock' : 'api.label.apiDocLock')
  return [{
    enabled: !isApi,
    icon: 'Edit',
    label: $i18nBundle('common.label.commonEdit', [label]),
    handler: () => {
      handlerData.showDocDetails(doc, true)
    }
  }, {
    enabled: !isApi,
    icon: 'DocumentCopy',
    label: $i18nBundle('common.label.commonCopy', [label]),
    handler: () => {
      $coreConfirm($i18nBundle('common.msg.commonConfirm', [$i18nBundle('common.label.commonCopy', [label])]))
        .then(() => copyApiDoc(doc.id))
        .then(() => handlerData.refreshProjectItem())
    }
  }, {
    icon: doc.locked ? 'LockOpenFilled' : 'LockFilled',
    label: lockedLabel,
    handler: () => {
      $coreConfirm($i18nBundle('common.msg.commonConfirm', [lockedLabel]))
        .then(() => docHandlerSaveDoc(doc, { locked: doc.locked ? 0 : 1 }))
        .then(() => handlerData.refreshProjectItem())
    }
  }, {
    icon: doc.status === 1 ? 'CheckBoxOutlined' : 'CheckBoxOutlineBlankFilled',
    type: doc.status === 1 ? 'warning' : '',
    label: $i18nBundle(statusLabel, [label]),
    handler: () => {
      $coreConfirm($i18nBundle('common.msg.commonConfirm', [$i18nBundle(statusLabel, [label])]))
        .then(() => docHandlerSaveDoc(doc, { status: doc.status === 1 ? 0 : 1 }))
        .then(() => handlerData.refreshProjectItem())
    }
  }, {
    enabled: !!handlerData.isDeletable?.value,
    icon: 'Delete',
    type: 'danger',
    label: $i18nBundle('common.label.commonDelete', [label]),
    handler: () => {
      const alertMsg = doc.docName ? $i18nBundle('common.msg.commonDeleteConfirm', [doc.docName]) : $i18nBundle('common.msg.deleteConfirm')
      $coreConfirm(alertMsg)
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
  const getToFolder = folderId => {
    let toFolder = folderTreeNodes.value[0]?.id
    if (folderId) {
      toFolder = folders.value?.find(folder => folder.id === folderId)?.id || toFolder
    }
    return toFolder
  }
  return { folderTreeNodes, folders, loadValidFolders, getToFolder }
}

export const getChildrenSortId = (folder) => {
  return getFolderChildrenSortId(folder, 10000, doc => !!doc.folderName)
}

export const getMdChildrenSortId = (folder) => {
  return getFolderChildrenSortId(folder, 10, doc => doc.docType === 'md')
}

export const getFolderChildrenSortId = (folder, defaultValue, filter = doc => doc?.docType === 'md') => {
  const mdChildren = folder.children?.filter(filter)
  const maxSort = defaultValue
  if (mdChildren?.length) {
    return mdChildren.reduce((sortId, child) => Math.max(sortId, child.sortId || 10), maxSort) + 10
  }
  return maxSort
}

export const getTreeNodesByKeys = (keys, treeNodes, nodeKey, foundNodes = []) => {
  if (keys.length) {
    for (const treeNode of treeNodes) {
      if (keys.includes(treeNode[nodeKey])) {
        foundNodes.push(treeNode)
      }
      if (treeNode.children?.length) {
        getTreeNodesByKeys(keys, treeNode.children, nodeKey, foundNodes)
      }
    }
    return foundNodes
  }
  return treeNodes
}

export const calcDetailPreferenceId = (apiDocDetail) => {
  return calcPreferenceId(apiDocDetail?.projectInfoDetail, apiDocDetail?.apiShare)
}

export const calcPreferenceId = (projectItem, apiShare) => {
  return apiShare?.shareId || projectItem?.projectCode || DEFAULT_PREFERENCE_ID_KEY
}

export const calcSharePreference = (projectItem, apiShare) => {
  const preferenceId = calcPreferenceId(projectItem, apiShare)
  return useShareConfigStore().sharePreferenceView[preferenceId]
}

export const calcShowMergeAllOf = (apiDocDetail) => {
  const preferenceId = calcDetailPreferenceId(apiDocDetail)
  const preference = useShareConfigStore().sharePreferenceView?.[preferenceId]
  return preference?.showMergeAllOf ?? true
}

export const calcTreeNodeChildNodes = (treeNode, draggingNode, type) => {
  const parentNode = type === 'inner' ? treeNode : treeNode.parent
  const childNodes = [...parentNode.childNodes]
  if (!childNodes.some(node => node.data.treeId === draggingNode.data.treeId)) {
    childNodes.push(draggingNode)
  }
  console.log('========================treeNode', treeNode, childNodes, draggingNode)
  const parentFolder = parentNode.data
  const docSorts = childNodes.filter(node => !!node.data.isDoc).map((node, index) => ({ docId: node.data.id, sortId: (index + 1) * 10 }))
  const folderSortId = Math.max(parentFolder.sortId, 10000)
  const folderSorts = childNodes.filter(node => !node.data.isDoc).map((node, index) => ({ folderId: node.data.id, sortId: folderSortId + (index + 1) * 10 }))
  return {
    folderId: parentFolder.id,
    projectId: parentFolder.projectId,
    sorts: [...docSorts, ...folderSorts]
  }
}

export const isTreeNodeFirstFolder = (treeNode) => {
  return treeNode.parent.childNodes.find(node => !node.isDoc) === treeNode
}

export const useInitShareDocTheme = (shareId) => {
  const shareDarkTheme = useDark({
    storageKey: `__${shareId}__vueuse-color-scheme_share`,
    selector: `html:has(.share-${shareId})`
  })
  provide(CURRENT_SHARE_THEME_KEY, shareDarkTheme)
  const checkDarkTheme = (shareDoc) => {
    const themeName = useShareConfigStore().sharePreferenceView[shareDoc?.shareId]?.defaultTheme ||
        shareDoc?.defaultTheme || 'dark'
    shareDarkTheme.value = themeName === 'dark'
  }
  checkDarkTheme()
  return {
    shareDarkTheme,
    checkDarkTheme
  }
}

export const useShareDocTheme = (sharePreference) => {
  const shareDarkTheme = inject(CURRENT_SHARE_THEME_KEY, null)
  const isDarkTheme = computed(() => sharePreference && shareDarkTheme ? shareDarkTheme.value : useGlobalConfigStore().isDarkTheme)
  return {
    isDarkTheme,
    toggleTheme: () => {
      if (shareDarkTheme) {
        console.log('====================shareDarkTheme', sharePreference, shareDarkTheme?.value)
        shareDarkTheme.value = !shareDarkTheme.value
        if (sharePreference) {
          sharePreference.defaultTheme = shareDarkTheme.value ? 'dark' : 'light'
        }
      }
    }
  }
}
