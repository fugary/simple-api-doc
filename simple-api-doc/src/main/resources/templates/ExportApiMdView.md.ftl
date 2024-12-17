# ${apiProject.projectName}

${apiProject.description}

<#if apiDocs?? && (apiDocs?size > 0)>
# ${message('api.label.docDetails')}

<#list apiDocs as apiDoc>
## ${apiDoc.docName}
<#if apiDoc.docType=='md'>
${apiDoc.docContent}
</#if>
<#if apiDoc.docType=='api'>
${apiDoc.apiMarkdown}
</#if>
</#list>
</#if>

<#if schemasMap?? && (schemasMap?size > 0)>
## ${message('api.label.apiModel')}

<#list schemasMap as name, schema>
### ${name}
<#if utils.isNotBlank(utils.getSchemaDescription(schema))>
${utils.getSchemaDescription(schema)}
</#if>

${utils.schemaToTable(schema)}
</#list>
</#if>
