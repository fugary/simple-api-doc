<#if withFrontmatter?? && withFrontmatter>
---
title: ${message('api.label.apiModel')}
order: 9999
---

</#if>
# ${message('api.label.apiModel')}

<#if schemasMap?? && (schemasMap?size > 0)>
<#list schemasMap as name, schema>
## ${name}

<#if utils.isNotBlank(utils.getSchemaDescription(schema))>
${utils.getSchemaDescription(schema)}

</#if>
${utils.schemaToTable(schema)}

</#list>
</#if>
