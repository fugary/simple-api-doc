# ${apiProject.projectName}

<#if utils.isNotBlank(apiVersion)>
* **${message('api.label.version')}**: ${apiVersion}
</#if>
<#if utils.isNotBlank(apiProject.description)>

${apiProject.description}
</#if>
<#if envList?? && (envList?size > 0)>

**${message('api.label.apiAddress')}**:
<#list envList as env>
* **${env.name!''}**: `${env.url!''}`
</#list>
</#if>
<#if apiDocs?? && (apiDocs?size > 0)>
<#assign currentFolder = "">
<#list apiDocs as apiDoc>
<#assign isSubFolder = utils.isNotBlank(apiDoc.folderPath)>
<#if (apiDoc.folderPath!"") != currentFolder>
<#assign currentFolder = (apiDoc.folderPath!"")>
<#if isSubFolder>

---

## 📁 ${currentFolder}
</#if>
</#if>
<#assign headingPrefix = isSubFolder?then("###", "##")>

<#if apiDoc.docType=='md'>
${headingPrefix} 📄 ${apiDoc.docName}

${apiDoc.docContent!""}
<#elseif apiDoc.docType=='api'>
<#if utils.isNotBlank(apiDoc.docName)><#assign docTitle = apiDoc.docName><#else><#assign docTitle = (apiDoc.url!"")></#if>
${headingPrefix} 🔗 <#if utils.isNotBlank(apiDoc.method)>[${apiDoc.method?upper_case}] </#if>${docTitle}<#if utils.isNotBlank(apiDoc.url) && docTitle != apiDoc.url> (`${apiDoc.url}`)</#if>

${apiDoc.apiMarkdown!""}
</#if>
</#list>
</#if>

<#if schemasMap?? && (schemasMap?size > 0)>

---

## 📦 ${message('api.label.apiModel')}

<#list schemasMap as name, schema>
### ${name}
<#if utils.isNotBlank(utils.getSchemaDescription(schema))>
${utils.getSchemaDescription(schema)}
</#if>

${utils.schemaToTable(schema)}
</#list>
</#if>
