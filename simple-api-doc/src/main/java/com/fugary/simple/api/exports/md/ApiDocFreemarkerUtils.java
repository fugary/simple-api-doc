package com.fugary.simple.api.exports.md;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SchemaJsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import io.swagger.v3.core.util.RefUtils;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.SpecVersion;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.fugary.simple.api.utils.SchemaJsonUtils.FORMATED_MAPPER;

@Slf4j
@Getter
@Setter
@Component
public class ApiDocFreemarkerUtils {

    @Autowired
    private MessageSource messageSource;

    private Parser markdownParser = Parser.builder().build();
    private HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    private static final Pattern SIMPLE_ANCHOR_PATTERN = Pattern.compile("^\\[([a-zA-Z0-9_.-]+)\\]\\(#\\1\\)$");

    private final Map<String, String> markdownHtmlCache = Collections.synchronizedMap(
            new LinkedHashMap<>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > 2048;
                }
            });

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
            if (StringUtils.isBlank(typeStr) && CollectionUtils.isNotEmpty(schema.getTypes())) {
                typeStr = StringUtils.join(schema.getTypes(), ",");
            }
            if (schema.getItems() != null) {
                typeStr = typeStr + "<" + propertyType(schema.getItems(), false) + ">";
            }
            List<Schema> xxxOf = getXxxOf(schema).getRight();
            if (CollectionUtils.isNotEmpty(xxxOf)) {
                typeStr = xxxOf.stream().map(xxx -> propertyType(xxx, false)).collect(Collectors.joining(", "));
            }
            String refLink = unRef(schema.get$ref());
            if (StringUtils.isNotBlank(refLink)) {
                typeStr = StringUtils.join("[" + refLink + "](#", refLink, ")");
            }
            String enumStr = getEnums(schema.getEnum());
            if (StringUtils.isNotBlank(enumStr)) {
                typeStr = StringUtils.join(typeStr, " (", getMessage("api.label.enums"), ": `", enumStr, "`)");
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
            Pair<String, List<Schema>> xxxOfPair = getXxxOf(schema);
            String xxxOfName = xxxOfPair.getKey();
            if (StringUtils.isNotBlank(xxxOfName)) {
                SimpleModelUtils.wrap(xxxOfPair.getRight()).forEach(allOf -> {
                    Pair<List<String>, Map<String, Schema>> allOfPair = getSchemaProperties(allOf);
                    required.addAll(allOfPair.getLeft());
                    properties.putAll(allOfPair.getRight());
                });
            }
            if (MapUtils.isNotEmpty(properties)) {
                properties.forEach((key, valueSchema) -> {
                    if (valueSchema != null) {
                        resultProperties.put(key, valueSchema);
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
    public Pair<String, List<Schema>> getXxxOf(Schema schema) {
        if (schema != null) {
            if (CollectionUtils.isNotEmpty(schema.getAllOf())) {
                return Pair.of("allOf", schema.getAllOf());
            }
            if (CollectionUtils.isNotEmpty(schema.getAnyOf())) {
                return Pair.of("anyOf", schema.getAnyOf());
            }
            if (CollectionUtils.isNotEmpty(schema.getOneOf())) {
                return Pair.of("oneOf", schema.getOneOf());
            }
        }
        return Pair.of("", new ArrayList<>());
    }

    /**
     * Schema转换成md的table，因为动态性比较强，直接用ftl不是很好生成
     *
     * @param schema
     * @return
     */
    public String schemaToTable(String schemaContent, String specVersion, List<ApiProjectInfoDetail> components) {
        SpecVersion version = SchemaJsonUtils.resolveSpecVersion(specVersion);
        Map<String, Schema<?>> schemasMap = new LinkedHashMap<>();
        SimpleModelUtils.processComponents(components, version, schemasMap);
        Schema<?> schema = SchemaJsonUtils.fromJson(schemaContent, Schema.class, SchemaJsonUtils.isV31(version));
        return schemaToTable(SchemaJsonUtils.getSchema(schema, schemasMap));
    }

    public String schemaToTable(Schema<?> schema) {
        Pair<List<String>, Map<String, Schema>> properties = getSchemaProperties(schema);
        if (MapUtils.isEmpty(properties.getRight())) {
            return schema == null ? StringUtils.EMPTY : getSchemaName(schema, false);
        }
        StringBuilder sb = fieldTableHeader();
        properties.getRight().forEach((name, property) -> appendFieldRow(sb, name, property,
                property.getDescription(), isRequired(properties.getLeft(), name),
                isTrue(property.getDeprecated()), schemaRemarks(property)));
        return sb.toString();
    }

    private StringBuilder fieldTableHeader() {
        StringBuilder sb = new StringBuilder("| ");
        for (String key : new String[]{"fieldName", "fieldType", "paramDesc", "defaultValue", "fieldRequired", "remark"}) {
            sb.append(getMessage("api.label." + key)).append(" | ");
        }
        return sb.append(System.lineSeparator()).append("| --- | --- | --- | --- | --- | --- |")
                .append(System.lineSeparator());
    }

    private void appendFieldRow(StringBuilder sb, String name, Schema<?> schema, String description,
                                boolean required, boolean deprecated, String remarks) {
        String fieldName = "**`" + name + "`**";
        sb.append("| ").append(tableCell(deprecated ? "~~" + fieldName + "~~" : fieldName)).append(" | ")
                .append(tableCell(getSchemaName(schema))).append(" | ")
                .append(tableCell(markdownToHtml(description))).append(" | ")
                .append(tableCell(getExample(schema == null ? null : schema.getDefault()))).append(" | ")
                .append(required ? "`Y`" : "N").append(" | ")
                .append(tableCell(remarks)).append(" | ").append(System.lineSeparator());
    }

    private String tableCell(String value) {
        return StringUtils.defaultString(value).replace("|", "&#124;")
                .replace("\r\n", "<br>").replace("\n", "<br>").replace("\r", "<br>");
    }

    private String schemaRemarks(Schema<?> schema) {
        if (schema == null) {
            return StringUtils.EMPTY;
        }
        List<String> remarks = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(schema.getEnum())) {
            remarks.add(getMessage("api.label.enums") + ": " + getEnums(schema.getEnum()));
        }
        if (schema.getExample() != null) {
            remarks.add(getMessage("api.label.example") + ": " + getExample(schema.getExample()));
        }
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("format", schema.getFormat());
        constraints.put("minimum", schema.getMinimum());
        constraints.put("maximum", schema.getMaximum());
        constraints.put("exclusiveMinimum", schema.getExclusiveMinimum());
        constraints.put("exclusiveMaximum", schema.getExclusiveMaximum());
        constraints.put("multipleOf", schema.getMultipleOf());
        constraints.put("minLength", schema.getMinLength());
        constraints.put("maxLength", schema.getMaxLength());
        constraints.put("pattern", schema.getPattern());
        constraints.put("minItems", schema.getMinItems());
        constraints.put("maxItems", schema.getMaxItems());
        constraints.put("uniqueItems", schema.getUniqueItems());
        constraints.put("minProperties", schema.getMinProperties());
        constraints.put("maxProperties", schema.getMaxProperties());
        constraints.put("nullable", schema.getNullable());
        constraints.put("readOnly", schema.getReadOnly());
        constraints.put("writeOnly", schema.getWriteOnly());
        constraints.put("deprecated", schema.getDeprecated());
        constraints.forEach((key, value) -> {
            if (value != null) {
                remarks.add(key + ": " + getExample(value));
            }
        });
        return String.join("<br>", remarks);
    }

    /**
     * 参数与模型使用相同六列，参数位置保留在备注中。
     */
    public String parametersToTable(List<Parameter> parameters) {
        if (CollectionUtils.isEmpty(parameters)) {
            return StringUtils.EMPTY;
        }
        StringBuilder sb = fieldTableHeader();
        for (Parameter parameter : parameters) {
            Schema<?> schema = parameter.getSchema();
            String description = StringUtils.defaultIfBlank(parameter.getDescription(),
                    schema == null ? null : schema.getDescription());
            List<String> remarks = new ArrayList<>();
            if (StringUtils.isNotBlank(parameter.getIn())) {
                remarks.add(getMessage("api.label.paramIn") + ": " + parameter.getIn());
            }
            if (parameter.getExample() != null) {
                remarks.add(getMessage("api.label.example") + ": " + getExample(parameter.getExample()));
            }
            String schemaRemark = schemaRemarks(schema);
            if (StringUtils.isNotBlank(schemaRemark)) {
                remarks.add(schemaRemark);
            }
            appendFieldRow(sb, parameter.getName(), schema, description, isTrue(parameter.getRequired()),
                    isTrue(parameter.getDeprecated()), String.join("<br>", remarks));
        }
        return sb.toString();
    }

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
            String exampleStr = getExample(schema.getExample());
            String defaultStr = getExample(schema.getDefault());
            String description = StringUtils.trimToEmpty(schema.getDescription());
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
     * 获取ApiSchema示例
     *
     * @param docSchema
     * @return
     */
    public String getApiSchemaExamples(ApiProjectInfoDetail docSchema, boolean v31) {
        if (docSchema != null && StringUtils.isNotBlank(docSchema.getExamples())) {
            List<Example> examples = SchemaJsonUtils.fromJson(docSchema.getExamples(), new TypeReference<List<Example>>() {
            }, v31);
            return SimpleModelUtils.wrap(examples)
                    .stream().filter(example -> example.getValue() != null)
                    .map(example -> StringUtils.join("```json\n",
                            SchemaJsonUtils.toJson(FORMATED_MAPPER, example.getValue()), "\n```"))
                    .collect(Collectors.joining("\n"));
        }
        return StringUtils.EMPTY;
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
     * 判断字符串中是否包含需要 Markdown/HTML 语法解析的特殊字符
     */
    private static boolean containsMarkdownChars(String str) {
        return StringUtils.containsAny(str, "*_`[]<>\n\r~#!&|\\");
    }

    /**
     * 使用 flexmark 将 Markdown 转换为 HTML（包含纯文本与锚点直出快路径，以及 LRU 缓存）
     *
     * @param markdown Markdown 格式字符串
     * @return HTML 格式字符串
     */
    public String markdownToHtml(String markdown) {
        if (StringUtils.isBlank(markdown)) {
            return "";
        }
        // 快路径 1：不含任何 Markdown 语法的纯文本（如 string、integer 等数据类型），直接返回
        if (!containsMarkdownChars(markdown)) {
            return markdown;
        }
        // 快路径 2：简单自身锚点链接 [Name](#Name)，直接拼接 HTML 标签直出
        Matcher anchorMatcher = SIMPLE_ANCHOR_PATTERN.matcher(markdown);
        if (anchorMatcher.matches()) {
            return "<a href=\"#" + anchorMatcher.group(1) + "\">" + anchorMatcher.group(1) + "</a>";
        }
        // 复杂 Markdown 内容走 LRU 缓存与 Flexmark 解析
        return markdownHtmlCache.computeIfAbsent(markdown, this::doMarkdownToHtml);
    }

    private String doMarkdownToHtml(String markdown) {
        // 使用 flexmark 解析 Markdown 并渲染为 HTML
        Node document = markdownParser.parse(markdown);
        String result = htmlRenderer.render(document);
        if (StringUtils.isNotBlank(result)) {
            result = StringUtils.replace(result, "\n", "");
            // 如果内容只在一个段落中，去掉 <p></p>
            if (result.startsWith("<p>") && result.endsWith("</p>")) {
                result = result.substring(3, result.length() - 4);
            }
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
     * 解析内联Schema默认名称
     *
     * @param schema
     * @param schemaName
     * @param fallback
     * @return
     */
    public String resolveInlineSchemaName(Schema<?> schema, String schemaName, String fallback) {
        if (schema != null && StringUtils.isNotBlank(schema.getName())) {
            return schema.getName();
        }
        if (StringUtils.isNotBlank(schemaName)) {
            return StringUtils.isNumeric(schemaName) ? fallback + "_" + schemaName : schemaName;
        }
        return fallback;
    }

    /**
     * 处理合并在一起的SchemaProperties
     *
     * @param schema
     * @param schemaName
     * @param schemaMap
     */
    public void calcInlineSchemaProperties(Schema<?> schema, String schemaName, Map<String, Schema<?>> schemaMap) {
        if (schema == null) {
            return;
        }
        if (schema.getItems() != null) {
            calcInlineSchemaProperties(schema.getItems(), schemaName, schemaMap);
            return;
        }
        List<Schema> xxxOf = getXxxOf(schema).getRight();
        if (StringUtils.isBlank(schema.getName()) && StringUtils.isBlank(schema.get$ref())
                && (MapUtils.isNotEmpty(schema.getProperties()) || CollectionUtils.isNotEmpty(xxxOf))) {
            String refName = schemaName;
            // 同名请求、响应和公共模型保留各自的字段与锚点。
            for (int suffix = 2; schemaMap.containsKey(refName) && schemaMap.get(refName) != schema; suffix++) {
                refName = schemaName + "_" + suffix;
            }
            schema.setName(refName);
            schema.set$ref(RefUtils.constructRef(refName));
            schemaMap.put(refName, schema);
        }
        String parentName = StringUtils.defaultIfBlank(schema.getName(), schemaName);
        if (CollectionUtils.isNotEmpty(xxxOf)) {
            for (Schema<?> subSchema : xxxOf) {
                calcInlineComposedProperties(subSchema, parentName, schemaMap);
            }
        }
        if (MapUtils.isNotEmpty(schema.getProperties())) {
            schema.getProperties().forEach((key, value) ->
                    calcInlineSchemaProperties(value, parentName + "." + key, schemaMap));
        }
    }

    /**
     * 处理组合 Schema (allOf/anyOf/oneOf) 的分支属性，不将分支展开为伪模型（如 allOf1, allOf2），
     * 而是直接将其包含的内联对象字段作为父模型的嵌套属性（parentName.propKey）进行处理。
     *
     * @param schema
     * @param parentName
     * @param schemaMap
     */
    private void calcInlineComposedProperties(Schema<?> schema, String parentName, Map<String, Schema<?>> schemaMap) {
        if (schema == null) {
            return;
        }
        if (schema.getItems() != null) {
            calcInlineComposedProperties(schema.getItems(), parentName, schemaMap);
            return;
        }
        // 如果是已有的公共组件引用（带 $ref 或 name），由组件本身展示，不需要作为匿名分支展开
        if (StringUtils.isNotBlank(schema.get$ref()) || StringUtils.isNotBlank(schema.getName())) {
            return;
        }
        List<Schema> xxxOf = getXxxOf(schema).getRight();
        if (CollectionUtils.isNotEmpty(xxxOf)) {
            for (Schema<?> subSchema : xxxOf) {
                calcInlineComposedProperties(subSchema, parentName, schemaMap);
            }
        }
        if (MapUtils.isNotEmpty(schema.getProperties())) {
            schema.getProperties().forEach((key, value) ->
                    calcInlineSchemaProperties(value, parentName + "." + key, schemaMap));
        }
    }

    /**
     * @param schema
     * @return
     */
    public String getSchemaName(Schema schema) {
        return getSchemaName(schema, true);
    }

    /**
     * @param schema
     * @param markdown
     * @return
     */
    public String getSchemaName(Schema schema, boolean markdown) {
        String schemaName = StringUtils.EMPTY;
        if (schema != null && StringUtils.isNotBlank(schema.getName())) {
            schemaName = StringUtils.join("[", schema.getName(), "](#", schema.getName(), ")");
        }
        if (StringUtils.isNotBlank(schemaName)) {
            return markdown ? markdownToHtml(schemaName) : schemaName;
        }
        return propertyType(schema, markdown);
    }

    /**
     * 获取xxxOf信息
     *
     * @param schema
     * @param markdown
     * @return
     */
    public String getXxxOfInfo(Schema schema, boolean markdown) {
        Pair<String, List<Schema>> xxxOfPair = getXxxOf(schema);
        String xxxOfName = xxxOfPair.getLeft();
        List<Schema> schemaList = xxxOfPair.getRight();
        if (StringUtils.isNotBlank(xxxOfName)) {
            Set<String> nameCheck = new HashSet<>();
            String xxxSchemaStr = schemaList.stream().map(allOf -> {
                String schemaName = getSchemaName(allOf, false);
                nameCheck.add(schemaName);
                return schemaName;
            }).collect(Collectors.joining(", "));
            boolean onlyObj = nameCheck.size() == 1 && nameCheck.contains("object"); // 在md中不显示
            if (StringUtils.isNotBlank(xxxSchemaStr) && !onlyObj) {
                xxxSchemaStr = StringUtils.join("**", xxxOfName, "[", schemaList.size(), "]**: ", xxxSchemaStr);
                return markdown ? markdownToHtml(xxxSchemaStr) : xxxSchemaStr;
            }
        }
        return StringUtils.EMPTY;
    }

    /**
     * 不为空判断
     *
     * @param str
     * @return
     */
    public boolean isNotBlank(String str) {
        return StringUtils.isNotBlank(str);
    }

    /**
     * 计算 ApiDoc 所启用的 SecuritySchemas
     *
     * @param apiDocDetail
     * @param specVersion
     * @return
     */
    public List<SecurityScheme> calcSecuritySchemas(ApiDocDetailVo apiDocDetail, SpecVersion specVersion) {
        if (apiDocDetail == null || apiDocDetail.getProjectInfoDetail() == null) {
            return Collections.emptyList();
        }
        ApiProjectInfoDetail securitySchemasDetail = apiDocDetail.getProjectInfoDetail().getSecuritySchemas();
        if (securitySchemasDetail == null || StringUtils.isBlank(securitySchemasDetail.getSchemaContent())) {
            return Collections.emptyList();
        }

        Map<String, SecurityScheme> allSchemas = SchemaJsonUtils.fromJson(securitySchemasDetail.getSchemaContent(),
                new TypeReference<>() {}, SchemaJsonUtils.isV31(specVersion));
        if (MapUtils.isEmpty(allSchemas)) {
            return Collections.emptyList();
        }

        ApiProjectInfoDetail securityRequirementsDetail = apiDocDetail.getSecurityRequirements();
        if (securityRequirementsDetail == null || StringUtils.isBlank(securityRequirementsDetail.getSchemaContent())) {
            securityRequirementsDetail = apiDocDetail.getProjectInfoDetail().getSecurityRequirements();
        }

        if (securityRequirementsDetail == null || StringUtils.isBlank(securityRequirementsDetail.getSchemaContent())) {
            return Collections.emptyList();
        }

        List<Map<String, List<String>>> requirements = SchemaJsonUtils.fromJson(securityRequirementsDetail.getSchemaContent(),
                new TypeReference<>() {}, SchemaJsonUtils.isV31(specVersion));
        if (CollectionUtils.isEmpty(requirements)) {
            return Collections.emptyList();
        }

        Set<String> activeKeys = new LinkedHashSet<>();
        for (Map<String, List<String>> reqMap : requirements) {
            if (reqMap != null) {
                activeKeys.addAll(reqMap.keySet());
            }
        }

        List<SecurityScheme> resultList = new ArrayList<>();
        for (String key : activeKeys) {
            for (Map.Entry<String, SecurityScheme> entry : allSchemas.entrySet()) {
                if (entry.getValue() != null && (StringUtils.equalsIgnoreCase(entry.getKey(), key) || StringUtils.equalsIgnoreCase(entry.getValue().getName(), key))) {
                    if (!resultList.contains(entry.getValue())) {
                        resultList.add(entry.getValue());
                    }
                }
            }
        }
        return resultList;
    }

    /**
     * 将 SecurityScheme 转换为格式化 Markdown 文本
     *
     * @param scheme
     * @return
     */
    public String formatSecurityScheme(SecurityScheme scheme) {
        if (scheme == null) {
            return StringUtils.EMPTY;
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(scheme.getName())) {
            sb.append("**`").append(scheme.getName()).append("`**");
        }
        if (scheme.getIn() != null) {
            sb.append(" (").append(scheme.getIn().toString().toLowerCase()).append(")");
        }
        if (scheme.getType() != null) {
            sb.append(" `<").append(scheme.getType().toString().toLowerCase()).append(">`");
        }
        if (StringUtils.isNotBlank(scheme.getScheme())) {
            sb.append(" ").append(scheme.getScheme());
        }
        if (StringUtils.isNotBlank(scheme.getDescription())) {
            sb.append(": ").append(scheme.getDescription());
        }
        return sb.toString();
    }
}
