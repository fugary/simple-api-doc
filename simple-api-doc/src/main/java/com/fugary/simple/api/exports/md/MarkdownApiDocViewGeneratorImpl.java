package com.fugary.simple.api.exports.md;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.entity.api.ApiDocSchema;
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
        ApiDocSchema parametersSchema = apiDocDetail.getParametersSchema();
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
        if (context.isGenerateComponents()) {
            model.put("schemasMap", schemasMap);
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

    @Override
    public void afterPropertiesSet() throws Exception {
        freemarkerConfig.setSharedVariable("message", freemarkerMessageMethod);
        freemarkerConfig.setSharedVariable("utils", apiDocFreemarkerUtils);
    }
}
