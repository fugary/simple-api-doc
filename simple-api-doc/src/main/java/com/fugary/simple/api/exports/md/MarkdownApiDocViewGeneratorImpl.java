package com.fugary.simple.api.exports.md;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.exports.ApiDocViewGenerator;
import com.fugary.simple.api.utils.SchemaJsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.apache.commons.collections.CollectionUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Create date 2024/12/13<br>
 *
 * @author gary.fu
 */
@Setter
@Getter
@Slf4j
@Component
public class MarkdownApiDocViewGeneratorImpl implements ApiDocViewGenerator, InitializingBean {

    @Autowired
    private Configuration freemarkerConfig; // FreeMarker 自动配置的 Configuration

    @Autowired
    private FreemarkerMessageMethod freemarkerMessageMethod;

    @Autowired
    private ApiDocFreemarkerUtils apiDocFreemarkerUtils;

    @Override
    public String generate(MdViewContext context) {
        ApiDocDetailVo apiDocDetail = context.getApiDocDetail();
        // 设置数据
        Map<String, Object> model = new HashMap<>();
        model.put("apiDocDetail", apiDocDetail);
        SpecVersion specVersion = SpecVersion.valueOf(apiDocDetail.getProjectInfoDetail().getSpecVersion());
        // 处理 schemasMap，传递给模板
        Map<String, Schema<?>> schemasMap = new LinkedHashMap<>();
        SimpleModelUtils.processComponents(apiDocDetail, specVersion, schemasMap);
        ApiProjectInfoDetail parametersSchema = apiDocDetail.getParametersSchema();
        if (parametersSchema != null && StringUtils.isNotBlank(parametersSchema.getSchemaContent())) {
            List<Parameter> parameters = SchemaJsonUtils.fromJson(parametersSchema.getSchemaContent(), new TypeReference<>() {
            }, SchemaJsonUtils.isV31(specVersion));
            if (!parameters.isEmpty()) {
                model.put("parameters", parameters);
            }
        }
        List<FmApiDocSchema> requestSchemas = apiDocDetail.getRequestsSchemas().stream()
                .map(requestSchema -> {
                    if (StringUtils.isNotBlank(requestSchema.getSchemaContent())) {
                        FmApiDocSchema newSchema = SimpleModelUtils.copy(requestSchema, FmApiDocSchema.class);
                        MediaType mediaType = SchemaJsonUtils.fromJson(requestSchema.getSchemaContent(),
                                MediaType.class, SchemaJsonUtils.isV31(specVersion));
                        if (mediaType != null && mediaType.getSchema() != null) {
                            newSchema.setSchema(SchemaJsonUtils.getSchema(mediaType.getSchema(), schemasMap));
                            Schema<?> schema = newSchema.getSchema();
                            Stack<String> schemaNames = new Stack<>();
                            schemaNames.push(StringUtils.defaultIfBlank(schema.getName(), "_request"));
                            apiDocFreemarkerUtils.calcInlineSchemaProperties(schema, schemaNames, schemasMap);
                        }
                        return newSchema;
                    }
                    return null;
                }).filter(Objects::nonNull).collect(Collectors.toList());
        model.put("requestsSchemas", requestSchemas);
        List<FmApiDocSchema> responseSchemas = apiDocDetail.getResponsesSchemas().stream()
                .map(responseSchema -> {
                    FmApiDocSchema newSchema = SimpleModelUtils.copy(responseSchema, FmApiDocSchema.class);
                    if (StringUtils.isNotBlank(newSchema.getSchemaContent())) {
                        MediaType mediaType = SchemaJsonUtils.fromJson(responseSchema.getSchemaContent(),
                                MediaType.class, SchemaJsonUtils.isV31(specVersion));
                        if (mediaType != null && mediaType.getSchema() != null) {
                            newSchema.setSchema(SchemaJsonUtils.getSchema(mediaType.getSchema(), schemasMap));
                            Schema<?> schema = newSchema.getSchema();
                            Stack<String> schemaNames = new Stack<>();
                            schemaNames.push(StringUtils.defaultIfBlank(schema.getName(), "_response"));
                            apiDocFreemarkerUtils.calcInlineSchemaProperties(schema, schemaNames, schemasMap);
                        }
                    }
                    return newSchema;
                }).sorted((docSchema1, docSchema2) -> {
                    int status1 = docSchema1.getStatusCode() == null ? 600 : docSchema1.getStatusCode();
                    int status2 = docSchema2.getStatusCode() == null ? 600 : docSchema2.getStatusCode();
                    return status1 - status2;
                }).collect(Collectors.toList());
        model.put("responsesSchemas", responseSchemas);
        model.put("v31", SchemaJsonUtils.isV31(specVersion));
        List<SecurityScheme> securitySchemas = apiDocFreemarkerUtils.calcSecuritySchemas(apiDocDetail, specVersion);
        if (CollectionUtils.isNotEmpty(securitySchemas)) {
            model.put("securitySchemas", securitySchemas);
        }
        if (context.isGenerateComponents()) {
            Map<String, Schema<?>> sortedSchemasMap = sortSchemasMap(schemasMap, requestSchemas, responseSchemas);
            model.put("schemasMap", sortedSchemasMap);
        }
        try {
            // 加载模板
            Template template = freemarkerConfig.getTemplate("ApiDocMdView.md.ftl");
            // 渲染模板
            return FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
        } catch (IOException | TemplateException e) {
            log.error("模板渲染失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 智能重排 schemasMap，使请求主模型、响应主模型优先排在最前，其次为关联嵌套模型
     *
     * @param schemasMap
     * @param requestSchemas
     * @param responseSchemas
     * @return
     */
    protected Map<String, Schema<?>> sortSchemasMap(Map<String, Schema<?>> schemasMap,
                                                    List<FmApiDocSchema> requestSchemas,
                                                    List<FmApiDocSchema> responseSchemas) {
        if (schemasMap == null || schemasMap.isEmpty()) {
            return schemasMap;
        }

        Set<String> directRequestNames = collectDirectSchemaNames(requestSchemas);
        Set<String> directResponseNames = collectDirectSchemaNames(responseSchemas);

        Set<String> nestedNames = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        visited.addAll(directRequestNames);
        visited.addAll(directResponseNames);

        // 使用单独的 startNames 进行遍历，避免在循环体内修改 visited 触发 ConcurrentModificationException
        List<String> startNames = new ArrayList<>(visited);
        for (String name : startNames) {
            if (schemasMap.containsKey(name)) {
                traverseNestedSchema(schemasMap.get(name), schemasMap, nestedNames, visited);
            }
        }

        Map<String, Schema<?>> sortedMap = new LinkedHashMap<>();

        // 按四层优先级顺序充填 sortedMap：1.请求主模型 2.响应主模型 3.嵌套关联模型
        List<Set<String>> priorityGroups = Arrays.asList(directRequestNames, directResponseNames, nestedNames);
        for (Set<String> group : priorityGroups) {
            for (String name : group) {
                if (schemasMap.containsKey(name) && !sortedMap.containsKey(name)) {
                    sortedMap.put(name, schemasMap.get(name));
                }
            }
        }

        // 4.项目其余未引用的通用模型
        for (Map.Entry<String, Schema<?>> entry : schemasMap.entrySet()) {
            if (!sortedMap.containsKey(entry.getKey())) {
                sortedMap.put(entry.getKey(), entry.getValue());
            }
        }

        return sortedMap;
    }

    protected Set<String> collectDirectSchemaNames(List<FmApiDocSchema> docSchemas) {
        Set<String> names = new LinkedHashSet<>();
        if (docSchemas != null) {
            for (FmApiDocSchema docSchema : docSchemas) {
                if (docSchema != null && docSchema.getSchema() != null) {
                    extractSchemaName(docSchema.getSchema(), names);
                }
            }
        }
        return names;
    }

    protected void extractSchemaName(Schema<?> schema, Set<String> names) {
        if (schema == null) {
            return;
        }
        if (StringUtils.isNotBlank(schema.getName())) {
            names.add(schema.getName());
        }
        if (StringUtils.isNotBlank(schema.get$ref())) {
            String refName = apiDocFreemarkerUtils.unRef(schema.get$ref());
            if (StringUtils.isNotBlank(refName)) {
                names.add(refName);
            }
        }
        if (schema.getItems() != null) {
            extractSchemaName(schema.getItems(), names);
        }
        for (Schema<?> subSchema : getXxxOf(schema)) {
            extractSchemaName(subSchema, names);
        }
    }

    protected void traverseNestedSchema(Schema<?> schema, Map<String, Schema<?>> schemasMap, Set<String> names, Set<String> visited) {
        if (schema == null) {
            return;
        }
        if (StringUtils.isNotBlank(schema.get$ref())) {
            String refName = apiDocFreemarkerUtils.unRef(schema.get$ref());
            if (StringUtils.isNotBlank(refName) && visited.add(refName)) {
                names.add(refName);
                if (schemasMap.containsKey(refName)) {
                    traverseNestedSchema(schemasMap.get(refName), schemasMap, names, visited);
                }
            }
        }
        if (schema.getItems() != null) {
            traverseNestedSchema(schema.getItems(), schemasMap, names, visited);
        }
        if (schema.getProperties() != null) {
            for (Object propObj : schema.getProperties().values()) {
                if (propObj instanceof Schema) {
                    traverseNestedSchema((Schema<?>) propObj, schemasMap, names, visited);
                }
            }
        }
        for (Schema<?> subSchema : getXxxOf(schema)) {
            traverseNestedSchema(subSchema, schemasMap, names, visited);
        }
    }

    protected List<Schema> getXxxOf(Schema<?> schema) {
        if (schema == null) {
            return Collections.emptyList();
        }
        List<Schema> list = new ArrayList<>();
        Optional.ofNullable(schema.getAllOf()).ifPresent(list::addAll);
        Optional.ofNullable(schema.getAnyOf()).ifPresent(list::addAll);
        Optional.ofNullable(schema.getOneOf()).ifPresent(list::addAll);
        return list;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        freemarkerConfig.setSharedVariable("message", freemarkerMessageMethod);
        freemarkerConfig.setSharedVariable("utils", apiDocFreemarkerUtils);
    }
}
