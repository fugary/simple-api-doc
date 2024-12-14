## ${message('api.label.basicInfo')}

<#if apiDocDetail.docName??>
* **${message('api.label.apiName')}：** ${apiDocDetail.docName}
</#if>
* **${message('api.label.method')}：** `${apiDocDetail.method}`
* **${message('api.label.requestPath')}：** `${apiDocDetail.url}`

<#if apiDocDetail.docContent?? || apiDocDetail.description??>
## ${message('api.label.apiDescription')}

<#if apiDocDetail.docContent??>
${apiDocDetail.docContent}
</#if>

<#if apiDocDetail.description??>
${apiDocDetail.description}
</#if>
</#if>
<#if parameters??>
## ${message('api.label.queryParams')}

| ${message('api.label.paramName')} | ${message('api.label.paramType')} | ${message('api.label.required')} | ${message('api.label.paramDesc')} |
| --- | --- | --- | --- |
<#list parameters as parameter>
| <#if utils.isTrue(parameter.getDeprecated())>~~`${parameter.getName()}`~~<#else>`${parameter.getName()}`</#if> | `${utils.propertyType(parameter.getSchema())}` | <#if utils.isTrue(parameter.getRequired())>`Y`</#if> | ${parameter.getDescription()!""} |
</#list>
</#if>

<#if requestsSchemas?? && (requestsSchemas?size > 0)>
## ${message('api.label.requestBody')}

<#list requestsSchemas as requestSchema>
### ${requestSchema.contentType}

<#if requestSchema.schema??>
<#assign schema=requestSchema.schema>
<#if utils.getSchemaProperties(schema)??>
| ${message('api.label.paramName')} | ${message('api.label.paramType')} | ${message('api.label.required')} | ${message('api.label.paramDesc')} |
| --- | --- | --- | --- |
<#list utils.getSchemaProperties(schema) as key, property>
| <#if utils.isTrue(property.getDeprecated())>~~`${key}`~~<#else>`${key}`</#if> | `${utils.propertyType(property)}` | <#if utils.isRequired(schema,key)>`Y`</#if> | ${property.getDescription()!""} |
</#list>
</#if>
</#if>
</#list>
</#if>

<#if responsesSchemas?? && (responsesSchemas?size > 0)>
## ${message('api.label.responseBody')}

<#list responsesSchemas as responseSchema>
### ${responseSchema.schemaName!''}

${responseSchema.description!""}

* **Content-Type**: ${responseSchema.contentType!""}
* **${message('api.label.statusCode')}**: ${responseSchema.statusCode!''}

<#if responseSchema.schema??>
<#assign schema=responseSchema.schema>
<#if utils.getSchemaProperties(schema)??>
| ${message('api.label.paramName')} | ${message('api.label.paramType')} | ${message('api.label.required')} | ${message('api.label.paramDesc')} |
| --- | --- | --- | --- |
<#list utils.getSchemaProperties(schema) as key, property>
| <#if utils.isTrue(property.getDeprecated())>~~`${key}`~~<#else>`${key}`</#if> | `${utils.propertyType(property)}` | <#if utils.isRequired(schema,key)>`Y`</#if> | ${property.getDescription()!""} |
</#list>
</#if>
</#if>
</#list>
</#if>

<#if schemasMap?? && (schemasMap?size > 0)>
## ${message('api.label.apiModel')}

<#list schemasMap as name, schema>
### ${name}
<#if utils.getSchemaProperties(schema)??>
| ${message('api.label.paramName')} | ${message('api.label.paramType')} | ${message('api.label.required')} | ${message('api.label.paramDesc')} |
| --- | --- | --- | --- |
<#list utils.getSchemaProperties(schema) as key, property>
| `${key}` | `${utils.propertyType(property)}` | <#if utils.isRequired(schema,key)>`Y`</#if> | ${property.getDescription()!""} |
</#list>
</#if>
</#list>
</#if>

