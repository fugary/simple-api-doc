import { ref } from 'vue'
import ApiFolderApi, { loadAvailableFolders } from '@/api/ApiFolderApi'
import { $coreAlert, $coreConfirm, processTreeData } from '@/utils'
import { $i18nBundle, $i18nConcat, $i18nKey } from '@/messages'
import ApiDocApi from '@/api/ApiDocApi'
import {
  checkExportDownloadDocs,
  downloadExportShareDocs
} from '@/api/SimpleShareApi'
import { isFunction } from 'lodash-es'
import { DEFAULT_PREFERENCE_ID_KEY } from '@/consts/ApiConstants'
import { useShareConfigStore } from '@/stores/ShareConfigStore'
import { checkExportProjectDocs, downloadExportProjectDocs } from '@/api/ApiProjectApi'

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

export const calcShowCleanHandlers = (folder, preference, config = {}) => {
  return preference.preferenceId
    ? [{
        enabled: !!folder.rootFlag,
        icon: 'DeleteFilled',
        labelKey: 'api.label.clearCachedData',
        type: 'danger',
        handler: () => {
          if (preference?.preferenceId) {
            useShareConfigStore().clearSharePreference(preference.preferenceId)
            const { refreshFolderTree } = config
            if (isFunction(refreshFolderTree)) {
              refreshFolderTree()
            }
          }
        }
      }]
    : []
}

export const getDownloadDocsHandlers = (projectItem, shareDoc, config = {}) => {
  const isShareDoc = shareDoc && !!shareDoc.shareId
  if (!isShareDoc || shareDoc?.debugEnabled) {
    const supportedTypes = ['json', 'yaml']
    const results = supportedTypes.map(type => {
      return {
        icon: `custom-icon-${type}`,
        label: $i18nKey('common.label.commonExport', `common.label.${type}`),
        handler: () => {
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
        }
      }
    })
    results.push(...supportedTypes.map(type => {
      return {
        icon: `custom-icon-${type}`,
        label: $i18nConcat($i18nBundle('api.label.exportSelectedApi'), $i18nBundle(`common.label.${type}`)),
        handler: () => {
          const { toShowTreeConfigWindow } = config
          if (isFunction(toShowTreeConfigWindow)) {
            toShowTreeConfigWindow(type)
          }
        }
      }
    }))
    return results
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
  }, calcShowDocLabelHandler(folder, preference), calcShowMergeAllOfHandler(folder, preference),
  ...calcShowCleanHandlers(folder, preference, handlerData), {
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

export const getChildrenSortId = (folder) => {
  const maxSort = 10
  return (folder?.children?.reduce((sortId, child) => Math.max(sortId, child.sortId || 10), maxSort) || maxSort) + 10
}

export const getMdChildrenSortId = (folder) => {
  return (folder?.children?.reduce((sortId, child) => Math.min(sortId, child.sortId), Infinity) || 10000) - 10
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

export const calcShowMergeAllOf = (apiDocDetail) => {
  const preferenceId = calcDetailPreferenceId(apiDocDetail)
  const preference = useShareConfigStore().sharePreferenceView?.[preferenceId]
  return preference?.showMergeAllOf ?? true
}
