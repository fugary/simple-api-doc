package com.fugary.simple.api.exports.md;

import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import io.swagger.v3.core.util.RefUtils;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Getter
@Setter
@Component
public class ApiDocFreemarkerUtils {

    @Autowired
    private MessageSource messageSource;

    private Parser markdownParser = Parser.builder().build();
    ;
    private HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    /**
     * 解析Ref
     *
     * @param ref
     * @return
     */
    public String unRef(String ref) {
        if (StringUtils.isNotBlank(ref)) {
            return (String) RefUtils.extractSimpleName(ref).getKey();
        }
        return StringUtils.EMPTY;
    }

    /**
     * 计算类型
     *
     * @param schema
     * @return
     */
    public String propertyType(Schema<?> schema) {
        return propertyType(schema, true);
    }

    /**
     * 计算类型
     *
     * @param schema
     * @param markdown
     * @return
     */
    public String propertyType(Schema<?> schema, boolean markdown) {
        if (schema != null) {
            String typeStr = schema.getType();
            if (schema instanceof ArraySchema) {
                typeStr = "array<" + propertyType(schema.getItems(), false) + ">";
            }
            List<Schema> xxxOf = getXxxOf(schema);
            if (CollectionUtils.isNotEmpty(xxxOf)) {
                typeStr = xxxOf.stream().map(xxx -> propertyType(xxx, false)).collect(Collectors.joining(", "));
            }
            String refLink = unRef(schema.get$ref());
            if (StringUtils.isNotBlank(refLink)) {
                typeStr = StringUtils.join("[" + refLink + "](#", refLink, ")");
            }
            return markdown ? markdownToHtml(typeStr) : typeStr;
        }
        return StringUtils.EMPTY;
    }

    /**
     * 属性是否是必填
     *
     * @param required
     * @param property
     * @return
     */
    public boolean isRequired(List<String> required, String property) {
        return SimpleModelUtils.wrap(required).contains(property);
    }

    /**
     * 是否是true
     *
     * @param bool
     * @return
     */
    public boolean isTrue(Boolean bool) {
        return Boolean.TRUE.equals(bool);
    }

    /**
     * 获取Schema属性
     *
     * @param schema
     * @return
     */
    public Pair<List<String>, Map<String, Schema>> getSchemaProperties(Schema<?> schema) {
        final List<String> required = new ArrayList<>(); // required
        final Map<String, Schema> resultProperties = new LinkedHashMap<>();
        if (schema != null) {
            final Map<String, Schema> properties = new LinkedHashMap<>();
            required.addAll(SimpleModelUtils.wrap(schema.getRequired()));
            properties.putAll(SimpleModelUtils.wrap(schema.getProperties()));
            if (schema.getItems() != null) {
                Pair<List<String>, Map<String, Schema>> arraySchemaPair = getSchemaProperties(schema.getItems());
                required.addAll(arraySchemaPair.getLeft());
                properties.putAll(arraySchemaPair.getRight());
            }
            boolean xxxOf = CollectionUtils.isNotEmpty(schema.getAllOf())
                    || CollectionUtils.isNotEmpty(schema.getAnyOf())
                    || CollectionUtils.isNotEmpty(schema.getOneOf());
            if (xxxOf) {
                SimpleModelUtils.wrap(schema.getAllOf()).forEach(allOf -> {
                    Pair<List<String>, Map<String, Schema>> allOfPair = getSchemaProperties(allOf);
                    required.addAll(allOfPair.getLeft());
                    properties.putAll(allOfPair.getRight());
                });
            }
            if (MapUtils.isNotEmpty(properties)) {
                properties.forEach((key, valueSchema) -> {
                    if (valueSchema != null) {
                        resultProperties.put(key, valueSchema);
                        if (StringUtils.isBlank(valueSchema.getName()) && MapUtils.isNotEmpty(valueSchema.getProperties())) {
                            valueSchema.getProperties().forEach((objKey, objValue) -> {
                                resultProperties.put(key + "." + objKey, (Schema) objValue);
                            });
                        }
                    }
                });
            }
        }
        return Pair.of(required, resultProperties);
    }

    /**
     * 获取xxxOf
     *
     * @param schema
     * @return
     */
    public List<Schema> getXxxOf(Schema schema) {
        if (schema != null) {
            if (CollectionUtils.isNotEmpty(schema.getAllOf())) {
                return schema.getAllOf();
            }
            if (CollectionUtils.isNotEmpty(schema.getAnyOf())) {
                return schema.getAnyOf();
            }
            if (CollectionUtils.isNotEmpty(schema.getOneOf())) {
                return schema.getOneOf();
            }
        }
        return new ArrayList<>();
    }

    /**
     * Schema转换成md的table，因为动态性比较强，直接用ftl不是很好生成
     *
     * @param schema
     * @return
     */
    public String schemaToTable(Schema<?> schema) {
        Pair<List<String>, Map<String, Schema>> schemaPropertiesPair = getSchemaProperties(schema);
        List<String> required = schemaPropertiesPair.getLeft();
        Map<String, Schema> schemaProperties = schemaPropertiesPair.getRight();
        if (MapUtils.isNotEmpty(schemaProperties)) {
            boolean hasDescription = schemaProperties.values().stream().anyMatch(this::checkDescription);
            StringBuilder sb = new StringBuilder();
            // 添加表头
            sb.append("| ").append(getMessage("api.label.paramName")).append(" | ")
                    .append(getMessage("api.label.paramType")).append(" | ")
                    .append(getMessage("api.label.required")).append(" | ");
            appendConditional(hasDescription, () -> sb.append(getMessage("api.label.paramDesc")).append(" |"));
            sb.append(System.lineSeparator());
            sb.append("| --- | --- | --- |");
            appendConditional(hasDescription, () -> sb.append(" --- |"));
            sb.append(System.lineSeparator());
            // 添加表内容
            schemaProperties.forEach((key, property) -> {
                sb.append("| ");
                // 判断是否标记为废弃
                if (isTrue(property.getDeprecated())) {
                    sb.append("~~`").append(key).append("~~`");
                } else {
                    sb.append("`").append(key).append("`");
                }
                sb.append(" | ")
                        .append(propertyType(property)).append(" | ");
                // 判断是否必填
                sb.append(isRequired(required, key) ? "`Y`" : "N").append(" | ");
                appendConditional(hasDescription, () -> sb.append(getSchemaDescription(property)).append(" | "));
                sb.append(System.lineSeparator());
            });
            return sb.toString();
        }
        return StringUtils.EMPTY; // 如果没有属性，返回空字符串
    }

    private boolean checkDescription(Schema schema) {
        return schema != null && (StringUtils.isNotBlank(schema.getDescription())
                || CollectionUtils.isNotEmpty(schema.getEnum()) || schema.getExample() != null || schema.getDefault() != null);
    }

    private void appendConditional(Supplier<Boolean> supplier, Supplier<StringBuilder> appender) {
        if (isTrue(supplier.get())) {
            appender.get();
        }
    }

    private void appendConditional(Boolean check, Supplier<StringBuilder> appender) {
        if (isTrue(check)) {
            appender.get();
        }
    }

    /**
     * 将 Parameters 转换为 Markdown 格式的表格
     *
     * @param parameters OpenAPI 参数列表
     * @return 生成的 Markdown 表格
     */
    public String parametersToTable(List<Parameter> parameters) {
        if (parameters != null && !parameters.isEmpty()) {
            boolean hasDescription = SimpleModelUtils.wrap(parameters).stream().anyMatch(parameter
                    -> StringUtils.isNotBlank(parameter.getDescription()) || parameter.getExample() != null || checkDescription(parameter.getSchema()));
            StringBuilder sb = new StringBuilder();
            // 添加表头
            sb.append("| ").append(getMessage("api.label.paramName")).append(" | ")
                    .append(getMessage("api.label.paramType")).append(" | ")
                    .append(getMessage("api.label.required")).append(" | ");
            sb.append(getMessage("api.label.paramIn")).append(" |");
            appendConditional(hasDescription, () -> sb.append(getMessage("api.label.paramDesc")).append(" | "));
            sb.append(System.lineSeparator());

            sb.append("| --- | --- | --- | --- |");
            appendConditional(hasDescription, () -> sb.append("--- | "));
            sb.append(System.lineSeparator());

            // 添加表内容
            for (Parameter parameter : parameters) {
                sb.append("| ");
                // 判断是否标记为废弃
                if (isTrue(parameter.getDeprecated())) {
                    sb.append("~~`").append(parameter.getName()).append("~~`");
                } else {
                    sb.append("`").append(parameter.getName()).append("`");
                }
                sb.append(" | ")
                        .append(propertyType(parameter.getSchema())).append(" | ")
                        .append(isTrue(parameter.getRequired()) ? "`Y`" : "N").append(" | ")
                        .append("*").append(parameter.getIn()).append("*").append(" | ");
                // 判断是否必填
                appendConditional(hasDescription, () -> sb.append(getSchemaDescription(parameter)).append(" |"));
                sb.append(System.lineSeparator());
            }
            return sb.toString();
        }
        return StringUtils.EMPTY; // 如果参数列表为空，返回空字符串
    }

    /**
     * 获取Schema描述
     *
     * @param target
     * @return
     */
    public String getSchemaDescription(Object target) {
        return getSchemaDescription(target, true);
    }

    /**
     * 获取Schema描述
     *
     * @param target
     * @param markdown
     * @return
     */
    public String getSchemaDescription(Object target, boolean markdown) {
        String result = StringUtils.EMPTY;
        if (target instanceof FmApiDocSchema) {
            FmApiDocSchema docSchema = (FmApiDocSchema) target;
            String schemaDesc = docSchema.getSchema() != null ? docSchema.getSchema().getDescription() : "";
            result = StringUtils.defaultIfBlank(docSchema.getDescription(), schemaDesc);
        } else if (target instanceof Schema) {
            Schema schema = (Schema) target;
            String enumStr = getEnums(schema.getEnum());
            String exampleStr = getExample(schema.getExample());
            String defaultStr = getExample(schema.getDefault());
            String description = StringUtils.trimToEmpty(schema.getDescription());
            if (StringUtils.isNotBlank(enumStr)) {
                result += StringUtils.join("**", getMessage("api.label.enums"), "**: ", enumStr, "<br>");
            }
            if (StringUtils.isNotBlank(exampleStr)) {
                result += StringUtils.join("**", getMessage("api.label.example"), "**: ", exampleStr, "<br>");
            }
            if (StringUtils.isNotBlank(defaultStr)) {
                result += StringUtils.join("**", getMessage("api.label.example"), "**: ", defaultStr, "<br>");
            }
            result += description;
        } else if (target instanceof Parameter) {
            Parameter parameter = (Parameter) target;
            String exampleStr = getExample(parameter.getExample());
            String description = StringUtils.trimToEmpty(parameter.getDescription());
            if (StringUtils.isNotBlank(exampleStr)) {
                result += StringUtils.join("**", getMessage("api.label.example"), "**: ", exampleStr, "<br>");
            }
            String schemaDescription = getSchemaDescription(parameter.getSchema(), false);
            if (StringUtils.isNotBlank(schemaDescription)) {
                result += schemaDescription;
            }
            result += description;
        }
        return markdown ? markdownToHtml(result) : result;
    }

    /**
     * 获取Example数据
     *
     * @param example
     * @return
     */
    public String getExample(Object example) {
        String exampleStr = StringUtils.EMPTY;
        if (example instanceof String) {
            exampleStr = (String) example;
        } else if (example != null) {
            exampleStr = JsonUtils.toJson(example);
        }
        if (StringUtils.isNotBlank(exampleStr)) {
            exampleStr = StringUtils.join("`", exampleStr, "`");
        }
        return exampleStr;
    }

    /**
     * 获取枚举数据
     *
     * @param enums
     * @return
     */
    public String getEnums(List<?> enums) {
        if (enums != null) {
            return enums.stream().map(Object::toString).map(value -> StringUtils.join("`", value, "`"))
                    .collect(Collectors.joining(", "));
        }
        return StringUtils.EMPTY;
    }

    /**
     * 使用 flexmark 将 Markdown 转换为 HTML
     *
     * @param markdown Markdown 格式字符串
     * @return HTML 格式字符串
     */
    private String markdownToHtml(String markdown) {
        if (StringUtils.isBlank(markdown)) {
            return "";
        }
        // 使用 flexmark 解析 Markdown 并渲染为 HTML
        Node document = markdownParser.parse(markdown);
        String result = htmlRenderer.render(document);
        if (StringUtils.isNotBlank(result)) {
            result = StringUtils.replace(result, "\n", "");
        }
        return result;
    }

    /**
     * 获取国际化消息
     *
     * @param code
     * @param args
     * @return
     */
    public String getMessage(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    /**
     * 处理合并在一起的SchemaProperties
     *
     * @param schema
     * @param schemaNames
     * @param schemaMap
     */
    public void calcInlineSchemaProperties(Schema schema, Stack<String> schemaNames, Map<String, Schema<?>> schemaMap) {
        List<Schema> xxxOf = getXxxOf(schema);
        if (CollectionUtils.isNotEmpty(xxxOf)) {
            xxxOf.forEach(xxx -> calcInlineSchemaProperties(xxx, schemaNames, schemaMap));
        }
        if (MapUtils.isNotEmpty(schema.getProperties())) {
            schema.getProperties().forEach((key, value) -> {
                if (value != null) {
                    String schemaKey = (String) key;
                    Schema valueSchema = (Schema) value;
                    if (valueSchema.getItems() != null) {
                        valueSchema = valueSchema.getItems();
                    }
                    if (StringUtils.isBlank(valueSchema.getName()) && MapUtils.isNotEmpty(valueSchema.getProperties())) {
                        schemaNames.push(schemaKey);
                        String refName = StringUtils.join(schemaNames, ".");
                        valueSchema.set$ref(RefUtils.constructRef(refName));
                        schemaMap.put(refName, valueSchema);
                        calcInlineSchemaProperties(valueSchema, schemaNames, schemaMap);
                        schemaNames.pop();
                    }
                }
            });
        }
    }
}
