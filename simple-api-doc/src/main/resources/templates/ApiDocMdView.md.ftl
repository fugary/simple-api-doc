### ${message('api.label.basicInfo')}

<#if apiDocDetail.docName??>
* **${message('api.label.apiName')}：** ${apiDocDetail.docName}
</#if>
* **${message('api.label.method')}：** `${apiDocDetail.method}`
* **${message('api.label.requestPath')}：** `${apiDocDetail.url}`

<#if apiDocDetail.docContent?? || apiDocDetail.description??>
### ${message('api.label.apiDescription')}

<#if apiDocDetail.docContent??>
${apiDocDetail.docContent}
</#if>

<#if apiDocDetail.description??>
${apiDocDetail.description}
</#if>
</#if>
<#if parameters??>
### ${message('api.label.queryParams')}

${utils.parametersToTable(parameters)}
</#if>

<#if requestsSchemas?? && (requestsSchemas?size > 0)>
### ${message('api.label.requestBody')}

<#list requestsSchemas as requestSchema>
#### ${requestSchema.contentType}

<#if utils.getSchemaDescription(requestSchema)??>
> ${utils.getSchemaDescription(requestSchema)}
</#if>
<#if requestSchema.schema?? && requestSchema.schema.getName()??>
* **${message('api.label.modelName')}**: **[${requestSchema.schema.getName()}](#${requestSchema.schema.getName()})**
</#if>
</#list>
</#if>

<#if responsesSchemas?? && (responsesSchemas?size > 0)>
### ${message('api.label.responseBody')}

<#list responsesSchemas as responseSchema>
#### ${responseSchema.schemaName!''} <#if utils.getSchemaDescription(responseSchema, false)??>${utils.getSchemaDescription(responseSchema, false)}</#if>

* **Content-Type**: ${responseSchema.contentType!""}
* **${message('api.label.statusCode')}**: ${responseSchema.statusCode!''}
<#if responseSchema.schema?? && responseSchema.schema.getName()??>
* **${message('api.label.modelName')}**: **[${responseSchema.schema.getName()}](#${responseSchema.schema.getName()})**
</#if>
</#list>
</#if>

<#if schemasMap?? && (schemasMap?size > 0)>
### ${message('api.label.apiModel')}

<#list schemasMap as name, schema>
#### ${name}
<#if utils.getSchemaDescription(schema)??>
${utils.getSchemaDescription(schema)}
</#if>

${utils.schemaToTable(schema)}
</#list>
</#if>

