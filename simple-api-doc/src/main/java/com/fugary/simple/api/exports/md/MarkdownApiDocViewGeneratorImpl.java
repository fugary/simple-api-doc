package com.fugary.simple.api.exports.md;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.entity.api.ApiDocSchema;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.exports.ApiDocViewGenerator;
import com.fugary.simple.api.utils.SchemaJsonUtils;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import io.swagger.v3.core.util.RefUtils;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Create date 2024/12/13<br>
 *
 * @author gary.fu
 */
@Slf4j
@Component
public class MarkdownApiDocViewGeneratorImpl implements ApiDocViewGenerator {

    @Override
    public String generate(ApiDocDetailVo apiDocDetail) {
        ApiProjectInfoDetailVo projectInfo = apiDocDetail.getProjectInfoDetail();
        SpecVersion specVersion = SpecVersion.valueOf(projectInfo.getSpecVersion());
        StringBuilder sb = new StringBuilder();
        sb.append("## 基本信息\n\n")
                .append("* **接口名称：** ").append(StringUtils.defaultIfBlank(apiDocDetail.getDocName(), apiDocDetail.getUrl())).append("\n")
                .append("* **请求方式：** ").append("`").append(apiDocDetail.getMethod()).append("`").append("\n")
                .append("* **请求路径：** ").append("`").append(apiDocDetail.getUrl()).append("`").append("\n")
                .append("\n\n");
        String docContent = StringUtils.defaultIfBlank(apiDocDetail.getDocContent(), apiDocDetail.getDescription());
        if (StringUtils.isNotBlank(docContent)) {
            sb.append("## 接口说明\n\n").append(docContent).append("\n\n");
        }
        List<ApiProjectInfoDetail> componentSchemas = projectInfo.getComponentSchemas();
        Map<String, Schema<?>> schemasMap = new LinkedHashMap<>(componentSchemas.size());
        componentSchemas.forEach(detail -> {
            Schema<?> schema = SchemaJsonUtils.fromJson(detail.getSchemaContent(), Schema.class, isV31(specVersion));
            schemasMap.put(detail.getSchemaName(), schema);
        });
        ApiDocSchema parametersSchema = apiDocDetail.getParametersSchema();
        if (parametersSchema != null && StringUtils.isNotBlank(parametersSchema.getSchemaContent())) {
            List<Parameter> parameters = SchemaJsonUtils.fromJson(parametersSchema.getSchemaContent(), new TypeReference<>() {
            }, isV31(specVersion));
            if (!parameters.isEmpty()) {
                sb.append("## 请求参数\n\n");
                // 输出Markdown格式表格
                sb.append("| 参数名称 | 参数类型 | 必填 | 参数说明 |\n");
                sb.append("| --- | --- | --- | --- |\n");
                for (Parameter parameter : parameters) {
                    sb.append("| ").append("`").append(parameter.getName()).append("`").append(" | ")
                            .append("`").append(parameter.getSchema().getType()).append("`").append(" | ")
                            .append(parameter.getRequired() ? "`true`" : "false").append(" | ")
                            .append(parameter.getDescription()).append(" |\n");
                }
                sb.append("\n\n");
            }
        }
        if (!apiDocDetail.getRequestsSchemas().isEmpty()) {
            sb.append("## 请求体\n\n");
            apiDocDetail.getRequestsSchemas().stream()
                    .filter(schema -> StringUtils.isNotBlank(schema.getSchemaContent()))
                    .forEach(schema -> {
                        sb.append("### ").append(schema.getContentType()).append("\n\n");
                        MediaType mediaType = SchemaJsonUtils.fromJson(schema.getSchemaContent(),
                                MediaType.class, isV31(specVersion));
                        if (mediaType != null) {
                            processSchema(mediaType.getSchema(), sb, schemasMap);
                        }
                    });
            sb.append("\n\n");
        }
        if (!apiDocDetail.getResponsesSchemas().isEmpty()) {
            sb.append("## 响应体\n\n");
            apiDocDetail.getResponsesSchemas()
                    .forEach(schema -> {
                        sb.append("### ").append(schema.getSchemaName()).append("\n\n");
                        sb.append(StringUtils.trimToEmpty(schema.getDescription())).append("\n\n");
                        if (StringUtils.isNotBlank(schema.getContentType())) {
                            sb.append("* **Content-Type**: ").append(schema.getContentType()).append("\n");
                        }
                        sb.append("* **状态码**: ").append(schema.getStatusCode()).append("\n");
                        sb.append("\n\n");
                        if (StringUtils.isNotBlank(schema.getSchemaContent())) {
                            MediaType mediaType = SchemaJsonUtils.fromJson(schema.getSchemaContent(),
                                    MediaType.class, isV31(specVersion));
                            if (mediaType != null) {
                                processSchema(mediaType.getSchema(), sb, schemasMap);
                            }
                        }
                    });
            sb.append("\n\n");
        }
        if (!schemasMap.isEmpty()) {
            sb.append("## API Model\n\n");
            schemasMap.forEach((name, schema) -> {
                sb.append("### ").append(name).append("\n\n");
                processSchema(schema, sb, schemasMap);
            });
        }
        return sb.toString();
    }

    protected void processSchema(Schema<?> schema, StringBuilder sb, Map<String, Schema<?>> schemasMap) {
        schema = getSchema(schema, schemasMap);
        if (schema != null) {
            Map<String, Schema> properties = schema.getProperties();
            if (schema.getItems() != null) {
                properties = getSchema(schema.getItems(), schemasMap).getProperties();
            }
            if (schema.getEnum() != null) {
                sb.append("* **enum**: `").append(StringUtils.join(schema.getEnum(), "`,`")).append("`\n");
            }
            boolean xxxOf = CollectionUtils.isNotEmpty(schema.getAllOf())
                    || CollectionUtils.isNotEmpty(schema.getAnyOf())
                    || CollectionUtils.isNotEmpty(schema.getOneOf());
            boolean hasChildren = MapUtils.isNotEmpty(properties) || xxxOf;
            if (hasChildren) {
                // schema输出Markdown格式表格
                sb.append("| 参数名称 | 参数类型 | 必填 | 参数说明 |\n");
                sb.append("| --- | --- | --- | --- |\n");
            }
            if (xxxOf) {
                processXxxOf(schema.getAllOf(), sb, schemasMap);
                processXxxOf(schema.getAnyOf(), sb, schemasMap);
                processXxxOf(schema.getOneOf(), sb, schemasMap);
            } else if (MapUtils.isNotEmpty(properties)) {
                // schema输出Markdown格式表格
                processSchemaProperties(sb, properties);
                sb.append("\n\n");
            }
        }
    }

    private void processXxxOf(List<Schema> xxxOf, StringBuilder sb, Map<String, Schema<?>> schemasMap) {
        wrap(xxxOf).forEach(allOf -> {
            Schema<?> calcSchema = getSchema(allOf, schemasMap);
            if (calcSchema != null) {
                processSchemaProperties(sb, calcSchema.getProperties());
            }
        });
    }

    private Schema<?> getSchema(Schema<?> schema, Map<String, Schema<?>> schemasMap) {
        if (StringUtils.isNotBlank(schema.get$ref())) {
            String refType = (String) RefUtils.extractSimpleName(schema.get$ref()).getKey();
            schema = schemasMap.get(refType);
        }
        return schema;
    }

    private void processSchemaProperties(StringBuilder sb, Map<String, Schema> properties) {
        if (properties != null) {
            properties.forEach((key, value) -> {
                sb.append("| ").append("`").append(key).append("`")
                        .append(" | ");
                String type = value.getType();
                if (StringUtils.isNotBlank(value.get$ref())) {
                    type = (String) RefUtils.extractSimpleName(value.get$ref()).getKey();
                }
                sb.append("`").append(type).append("`");
                sb.append(" | ");
                if (value.getRequired() != null) {
                    sb.append(value.getRequired().contains(key) ? "`true`" : "false");
                }
                sb.append(" | ");
                sb.append(StringUtils.trimToEmpty(value.getDescription())).append(" |\n");
            });
        }
    }

    protected boolean isV31(SpecVersion specVersion) {
        return SpecVersion.V31.equals(specVersion);
    }

    private <T> Collection<T> wrap(Collection<T> collection) {
        if (collection == null) {
            return new ArrayList<>();
        }
        return collection;
    }
}
