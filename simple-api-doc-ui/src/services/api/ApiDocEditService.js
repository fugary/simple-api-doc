import { $coreError } from '@/utils'
import { $i18nBundle } from '@/messages'
import ApiProjectInfoDetailApi from '@/api/ApiProjectInfoDetailApi'
import { ElMessage } from 'element-plus'
import { SCHEMA_SELECT_TYPE } from '@/consts/ApiConstants'
import { fromModelToSchema, hasXxxOf } from '@/services/api/ApiDocPreviewService'
import { cloneDeep } from 'lodash-es'

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
    pathParams: initApiDocParams(calcPathParams(schemaItems, docDetail.url)),
    requestParams: initApiDocParams(schemaItems.filter(item => item.in === 'query')),
    headerParams: initApiDocParams(schemaItems.filter(item => item.in === 'header'))
  }
}

export const initApiDocParams = params => {
  return params.map(param => {
    if (!param.__type) {
      param.__type = param.schema?.type
      if (param?.schema?.$ref) {
        param.__type = SCHEMA_SELECT_TYPE.REF
      } else if (hasXxxOf(param.schema)) {
        param.__type = SCHEMA_SELECT_TYPE.XXX_OF
      }
    }
    if (param.schema) {
      param.schema.description = param.description
    }
    return param
  })
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

export const processSchemaBeforeSave = (model, additionalPropertiesEnabled) => {
  const type = model.type
  if (type) {
    const toClean = ['type', '$ref', 'allOf', 'oneOf', 'anyOf']
    toClean.forEach(key => delete model.schema[key])
    if (model.dataType === SCHEMA_SELECT_TYPE.BASIC) {
      model.schema.type = type
      if (type === 'array' && model[type].length) {
        model.schema.items = fromModelToSchema(model[type][0])
      }
      if (type === 'object') {
        delete model.schema.additionalProperties
        if (additionalPropertiesEnabled && model[type].length) {
          model.schema.additionalProperties = fromModelToSchema(model[type][0])
        }
      }
    } else if (model.dataType === SCHEMA_SELECT_TYPE.REF) {
      model.schema = {
        $ref: type,
        description: model.schema?.description
      }
    } else if (model.dataType === SCHEMA_SELECT_TYPE.XXX_OF) {
      console.log('============type', type, model[type])
      model.schema = {
        [type]: model[type].map(fromModelToSchema),
        description: model.schema?.description
      }
    }
  }
}

export const processProjectInfos = (projectItem) => {
  return projectItem.infoList?.map(info => {
    info = cloneDeep(info)
    if (info.folderId && projectItem.folders?.length) {
      const folder = projectItem.folders.find(item => item.id === info.folderId)
      if (folder && !folder.rootFlag) {
        info.folderName = folder.folderName
      }
    }
    return info
  }) || []
}

export const processSecuritySchema = (securityInfo, schema) => {
  schema = cloneDeep(schema)
  if (schema.type !== 'openIdConnect') {
    delete schema.openIdConnectUrl
  }
  if (schema.type !== 'oauth2') {
    delete schema.flows
  } else if (schema.flows) {
    const oauth2Type = securityInfo.oauth2Type
    schema.flows = {
      [oauth2Type]: schema.flows[oauth2Type]
    }
  }
  return schema
}
