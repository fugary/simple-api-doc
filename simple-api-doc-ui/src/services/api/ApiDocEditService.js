import { $coreError } from '@/utils'
import { $i18nBundle } from '@/messages'
import ApiProjectInfoDetailApi from '@/api/ApiProjectInfoDetailApi'
import { ElMessage } from 'element-plus'

/**
 * 模型初始化计算
 *
 * @param docDetail
 */
export const calcApiDocRequestModel = docDetail => {
  const parametersSchema = docDetail.parametersSchema || {
    ...newDocInfoDetail(docDetail),
    bodyType: 'parameters'
  }
  const schemaItems = JSON.parse(parametersSchema.schemaContent || '[]')
  console.log('==========================docDetail', docDetail, parametersSchema.schemaContent)
  return {
    parametersSchema,
    requestParams: schemaItems.filter(item => item.in === 'query'),
    pathParams: schemaItems.filter(item => item.in === 'path'),
    headerParams: schemaItems.filter(item => item.in === 'header')
  }
}

export const toParametersSchemaContent = (paramsModel) => {
  const parameters = [...paramsModel.pathParams, ...paramsModel.requestParams, ...paramsModel.headerParams]
    .filter(param => !!param?.name)
  return JSON.stringify(parameters)
}

export const checkAndSaveDocInfoDetail = (data) => {
  try {
    JSON.parse(data.schemaContent)
  } catch (e) {
    $coreError($i18nBundle('common.msg.jsonError'))
    return
  }
  return ApiProjectInfoDetailApi.saveOrUpdate(data)
    .then((data) => {
      if (data.success) {
        ElMessage.success($i18nBundle('common.msg.saveSuccess'))
        return data.resultData
      }
    })
}

export const newDocInfoDetail = docDetail => {
  return {
    projectId: docDetail.projectId,
    infoId: docDetail.infoId,
    bodyType: 'component',
    docId: docDetail.id
  }
}
