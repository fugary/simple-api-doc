package com.fugary.simple.api.exports.md;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.entity.api.ApiDocSchema;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.exports.ApiDocViewGenerator;
import com.fugary.simple.api.utils.SchemaJsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import io.swagger.v3.core.util.RefUtils;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
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
    public String generate(ApiDocDetailVo apiDocDetail) {
        // 设置数据
        Map<String, Object> model = new HashMap<>();
        model.put("locale", LocaleContextHolder.getLocale());
        model.put("apiDocDetail", apiDocDetail);
        SpecVersion specVersion = SpecVersion.valueOf(apiDocDetail.getProjectInfoDetail().getSpecVersion());
        // 处理 schemasMap，传递给模板
        Map<String, Schema<?>> schemasMap = new LinkedHashMap<>();
        processComponents(apiDocDetail, specVersion, schemasMap);
        model.put("schemasMap", schemasMap);
        ApiDocSchema parametersSchema = apiDocDetail.getParametersSchema();
        if (parametersSchema != null && StringUtils.isNotBlank(parametersSchema.getSchemaContent())) {
            List<Parameter> parameters = SchemaJsonUtils.fromJson(parametersSchema.getSchemaContent(), new TypeReference<>() {
            }, isV31(specVersion));
            if (!parameters.isEmpty()) {
                model.put("parameters", parameters);
            }
        }
        List<String> reqOrResNames = new ArrayList<>();
        List<FmApiDocSchema> requestSchemas = apiDocDetail.getRequestsSchemas().stream()
                .map(requestSchema -> {
                    if (StringUtils.isNotBlank(requestSchema.getSchemaContent())) {
                        FmApiDocSchema newSchema = SimpleModelUtils.copy(requestSchema, FmApiDocSchema.class);
                        MediaType mediaType = SchemaJsonUtils.fromJson(requestSchema.getSchemaContent(),
                                MediaType.class, isV31(specVersion));
                        if (mediaType != null && mediaType.getSchema() != null) {
                            newSchema.setSchema(getSchema(mediaType.getSchema(), schemasMap));
                            if (StringUtils.isNotBlank(newSchema.getSchema().getName())) {
                                reqOrResNames.add(newSchema.getSchema().getName());
                            }
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
                                MediaType.class, isV31(specVersion));
                        if (mediaType != null && mediaType.getSchema() != null) {
                            newSchema.setSchema(getSchema(mediaType.getSchema(), schemasMap));
                            if (StringUtils.isNotBlank(newSchema.getSchema().getName())) {
                                reqOrResNames.add(newSchema.getSchema().getName());
                            }
                        }
                    }
                    return newSchema;
                }).sorted((docSchema1, docSchema2) -> {
                    int status1 = docSchema1.getStatusCode() == null ? 600 : docSchema1.getStatusCode();
                    int status2 = docSchema2.getStatusCode() == null ? 600 : docSchema2.getStatusCode();
                    return status1 - status2;
                }).collect(Collectors.toList());
        model.put("responsesSchemas", responseSchemas);
        reqOrResNames.forEach(schemasMap::remove);
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

    private void processComponents(ApiDocDetailVo apiDocDetail, SpecVersion specVersion, Map<String, Schema<?>> schemasMap) {
        List<ApiProjectInfoDetail> componentSchemas = apiDocDetail.getProjectInfoDetail().getComponentSchemas();
        componentSchemas.forEach(detail -> {
            Schema<?> schema = SchemaJsonUtils.fromJson(detail.getSchemaContent(), Schema.class, isV31(specVersion));
            schemasMap.put(detail.getSchemaName(), getSchema(schema, schemasMap));
        });
    }

    private void processXxxOf(List<Schema> xxxOf, Map<String, Schema<?>> schemasMap) {
        xxxOf = SimpleModelUtils.wrap(xxxOf);
        for (int i = 0; i < xxxOf.size(); i++) {
            Schema<?> calcSchema = getSchema(xxxOf.get(i), schemasMap);
            if (calcSchema != null) {
                xxxOf.set(i, calcSchema);
            }
        }
    }

    private Schema<?> getSchema(Schema<?> schema, Map<String, Schema<?>> schemasMap) {
        if (StringUtils.isNotBlank(schema.get$ref())) {
            String refType = (String) RefUtils.extractSimpleName(schema.get$ref()).getKey();
            schema = schemasMap.get(refType);
            if (schema != null && StringUtils.isBlank(schema.getName())) {
                schema.setName(refType);
            }
        }
        boolean xxxOf = CollectionUtils.isNotEmpty(schema.getAllOf())
                || CollectionUtils.isNotEmpty(schema.getAnyOf())
                || CollectionUtils.isNotEmpty(schema.getOneOf());
        if (xxxOf) {
            processXxxOf(schema.getAllOf(), schemasMap);
            processXxxOf(schema.getAnyOf(), schemasMap);
            processXxxOf(schema.getOneOf(), schemasMap);
        }
        return schema;
    }

    private boolean isV31(SpecVersion specVersion) {
        return SpecVersion.V31.equals(specVersion);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        freemarkerConfig.setSharedVariable("message", freemarkerMessageMethod);
        freemarkerConfig.setSharedVariable("utils", apiDocFreemarkerUtils);
    }
}
