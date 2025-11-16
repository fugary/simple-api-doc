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
    pathParams: calcPathParams(schemaItems, docDetail.url),
    requestParams: schemaItems.filter(item => item.in === 'query'),
    headerParams: schemaItems.filter(item => item.in === 'header')
  }
}

export const extractPathParams = (pathTemplate) => {
  const regex = /{([^}]+)}/g
  const params = []
  let match
  while ((match = regex.exec(pathTemplate)) !== null) {
    params.push(match[1]) // 提取参数名
  }
  return params
}

export const calcPathParams = (schemaItems, url) => {
  // 从 schema 中显式声明的 path 参数
  const schemaPathParams = schemaItems.filter(item => item.in === 'path')
  const schemaNames = schemaPathParams.map(item => item.name)
  // 从 URL 中解析出来的参数名
  const urlParamNames = extractPathParams(url || '')
  // URL 中存在但 schema 中未声明的参数
  const missingParams = urlParamNames.filter(
    name => !schemaNames.includes(name)
  )
  // 合并 schema 参数和 URL 补充出的参数
  const combinedParams = [
    ...schemaPathParams,
    ...missingParams.map(name => ({ name, required: true, schema: { type: 'string' } }))
  ]
  // 最终只保留 URL 中真实存在的参数
  return combinedParams.filter(param =>
    urlParamNames.includes(param.name)
  )
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
